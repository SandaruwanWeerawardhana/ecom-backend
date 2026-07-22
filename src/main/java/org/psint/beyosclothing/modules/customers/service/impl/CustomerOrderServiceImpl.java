package org.psint.beyosclothing.modules.customers.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.core.exception.BadRequestException;
import org.psint.beyosclothing.core.exception.ResourceNotFoundException;
import org.psint.beyosclothing.modules.auth.entity.User;
import org.psint.beyosclothing.modules.auth.repository.UserRepository;
import org.psint.beyosclothing.modules.customers.dto.request.AddToCartRequestDTO;
import org.psint.beyosclothing.modules.customers.dto.request.CreateWishlistItemRequest;
import org.psint.beyosclothing.modules.customers.dto.request.PlaceOrderRequest;
import org.psint.beyosclothing.modules.customers.dto.request.UpdateOrderPaymentStatusRequest;
import org.psint.beyosclothing.modules.customers.dto.response.CartResponseDTO;
import org.psint.beyosclothing.modules.customers.dto.response.CustomerDashboardCountsResponse;
import org.psint.beyosclothing.modules.customers.dto.response.OrderDetailResponse;
import org.psint.beyosclothing.modules.customers.dto.response.OrderPaymentStatusResponse;
import org.psint.beyosclothing.modules.customers.dto.response.WishlistItemResponse;
import org.psint.beyosclothing.modules.customers.entity.Customer;
import org.psint.beyosclothing.modules.customers.entity.WishlistItem;
import org.psint.beyosclothing.modules.customers.repository.CustomerRepository;
import org.psint.beyosclothing.modules.customers.repository.WishlistItemRepository;
import org.psint.beyosclothing.modules.customers.service.CustomerOrderService;
import org.psint.beyosclothing.modules.inventory.dto.request.InventoryUpdateRequest;
import org.psint.beyosclothing.modules.inventory.dto.response.InventoryUpdateResponse;
import org.psint.beyosclothing.modules.payment.dto.external.PaymentRequestCreationRequest;
import org.psint.beyosclothing.modules.payment.dto.external.PaymentRequestCreationResponse;
import org.psint.beyosclothing.modules.sms.service.OrderSmsNotificationService;
import org.psint.beyosclothing.shared.dto.CartItemsLookupRequest;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Customer Order Service Implementation
 * Uses RabbitMQ request/reply for cross-module order operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CustomerOrderServiceImpl implements CustomerOrderService {

    private final CustomerRepository customerRepository;
    private final WishlistItemRepository wishlistItemRepository;
    private final UserRepository userRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final OrderSmsNotificationService smsNotificationService;

    @Value("${app.rabbitmq.exchange.product}")
    private String productExchange;

    @Value("${app.rabbitmq.exchange.cart}")
    private String cartExchange;

    @Value("${app.rabbitmq.queue.cart-items-clear-request:cart.items.clear.request.queue}")
    private String cartItemsClearRequestQueue;

    @Value("${app.rabbitmq.exchange.order:beyos.exchange.order}")
    private String orderExchange;

    @Value("${app.rabbitmq.exchange.inventory:beyos.exchange.inventory}")
    private String inventoryExchange;

    @Value("${app.rabbitmq.exchange.payment:beyos.exchange.payment}")
    private String paymentExchange;

    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;

    @Override
    public OrderDetailResponse createOrder(PlaceOrderRequest request, String customerUuid) {
        log.info("Creating customer order for customerUuid: {}", customerUuid);

        Customer customer = customerRepository.findByUuid(customerUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with UUID: " + customerUuid));

        // Customer.email is transient (the real value lives in the auth User table), so populate it
        // from the auth module before it is used for the order record and the payment gateway.
        customer.setEmail(resolveCustomerEmail(customer.getUserId()));

        Map<String, Object> cartResponse = lookupCartItemsForOrder(customer.getUuid(), request.getCartItemUuids());
        List<Map<String, Object>> cartItems = getMapList(cartResponse, "items");
        if (!Boolean.TRUE.equals(cartResponse.get("found")) || cartItems.isEmpty()) {
            throw new BadRequestException("Cart is empty or selected items were not found");
        }

        BigDecimal subtotal = getBigDecimalOrZero(cartResponse, "subtotal");
        BigDecimal promoDiscount = Boolean.FALSE.equals(request.getUsePromoCode())
                ? BigDecimal.ZERO
                : getBigDecimalOrZero(cartResponse, "promoDiscount");
        BigDecimal deliveryCharges = BigDecimal.ZERO;
        BigDecimal total = subtotal.subtract(promoDiscount).add(deliveryCharges);

        Map<String, Object> orderRequest = new HashMap<>();
        orderRequest.put("requestId", UUID.randomUUID().toString());
        orderRequest.put("orderType", "CUSTOMER");
        orderRequest.put("customerId", customer.getId());
        orderRequest.put("customerUuid", customer.getUuid());
        orderRequest.put("customerName", customer.getFullName());
        orderRequest.put("customerPhone", customer.getPhone());
        orderRequest.put("customerEmail", customer.getEmail());
        orderRequest.put("cartId", getLong(cartResponse, "cartId"));
        orderRequest.put("deliveryAddressId", request.getDeliveryAddressId());
        orderRequest.put("courierId", request.getCourierId());
        orderRequest.put("paymentMethodId", request.getPaymentMethodId());
        orderRequest.put("paymentMethod", String.valueOf(request.getPaymentMethodId()));
        orderRequest.put("notes", request.getCustomerNotes());
        orderRequest.put("subtotal", subtotal);
        orderRequest.put("promoDiscount", promoDiscount);
        orderRequest.put("deliveryCharges", deliveryCharges);
        orderRequest.put("total", total);
        orderRequest.put("items", cartItems.stream()
                .map(this::mapCartItemToOrderItemRequest)
                .toList());

        Map<String, Object> orderResponse = sendOrderRequest("order.create.request", orderRequest);
        if (!Boolean.TRUE.equals(orderResponse.get("success"))) {
            String error = getString(orderResponse, "error");
            throw new BadRequestException(error != null ? error : "Order creation failed");
        }

        // Initiate payment. For online methods (e.g. OnePay hosted checkout) this returns a redirect
        // URL and the order stays PENDING/UNPAID until the gateway confirms the payment succeeded.
        PaymentInitiation payment = createPaymentRequest(customer, request, orderResponse, total);

        // Online payments are finalized only after the payment-success callback, so an abandoned
        // checkout never consumes stock, empties the cart or sends a confirmation SMS. Offline methods
        // (e.g. Cash on Delivery) have no redirect URL and are finalized immediately here.
        if (!hasText(payment.redirectUrl())) {
            finalizeCustomerOrder(orderResponse, cartResponse, cartItems, total);
        }

        return OrderDetailResponse.builder()
                .orderUuid(getString(orderResponse, "orderUuid"))
                .orderDate(parseDateTime(orderResponse.get("orderDate")))
                .deliveryDate(null)
                .amount(total)
                .type("CUSTOMER")
                .status(getString(orderResponse, "status"))
                .paymentRedirectUrl(payment.redirectUrl())
                .ipgTransactionId(payment.gatewayTransactionId())
                .build();
    }

    /**
     * Creates the payment request in the payment module (RabbitMQ RPC). For an online method such as
     * OnePay this triggers hosted-checkout creation and returns the redirect URL the frontend uses;
     * for offline methods (e.g. COD) the redirect URL is null. A failed initiation is treated as a
     * hard error so the customer is never handed an online order with no gateway URL.
     */
    private PaymentInitiation createPaymentRequest(Customer customer, PlaceOrderRequest request,
                                                   Map<String, Object> orderResponse, BigDecimal total) {
        String orderUuid = getString(orderResponse, "orderUuid");

        PaymentRequestCreationRequest paymentRequest = PaymentRequestCreationRequest.builder()
                .requestId(UUID.randomUUID().toString())
                .orderId(getLong(orderResponse, "orderId"))
                .orderUuid(orderUuid)
                .orderNumber(getString(orderResponse, "orderNumber"))
                .methodId(request.getPaymentMethodId())
                .amount(total)
                .currency("LKR")
                .returnUrl(frontendBaseUrl + "/checkout/payment-status?orderUuid=" + orderUuid)
                .cancelUrl(frontendBaseUrl + "/checkout/payment-cancel")
                .customerFirstName(customer.getFirstName())
                .customerLastName(customer.getLastName())
                .customerEmail(customer.getEmail())
                .customerPhone(customer.getPhoneNumber())
                .build();

        try {
            Object response = rabbitTemplate.convertSendAndReceive(
                    paymentExchange, "payment.request.create.request", paymentRequest);
            if (response == null) {
                throw new BadRequestException("Payment service did not return a response");
            }

            PaymentRequestCreationResponse paymentResponse = objectMapper.convertValue(
                    parseLookupResponse(response), PaymentRequestCreationResponse.class);
            if (!Boolean.TRUE.equals(paymentResponse.getSuccess())) {
                String error = paymentResponse.getErrorMessage();
                throw new BadRequestException(error != null ? error : "Payment initiation failed");
            }

            return new PaymentInitiation(
                    paymentResponse.getRedirectUrl(),
                    paymentResponse.getGatewayTransactionId());
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Payment request creation failed for order {}", orderUuid, e);
            throw new BadRequestException("Payment initiation failed: " + e.getMessage());
        }
    }

    private record PaymentInitiation(String redirectUrl, String gatewayTransactionId) {
    }

    /**
     * Resolves the customer's email from the auth User record (where it is actually stored) using the
     * customer's userId. Returns null when it cannot be found so callers can apply their own fallback.
     */
    private String resolveCustomerEmail(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId)
                .map(User::getEmail)
                .orElse(null);
    }

    /**
     * Applies the side effects that finalize an order whose payment is settled at placement time
     * (offline methods such as Cash on Delivery): decreases inventory, sends the order-confirmation
     * SMS and clears the ordered cart items. For online payments these same actions are deferred to
     * the payment-success callback so they run only once the gateway confirms the payment.
     */
    private void finalizeCustomerOrder(Map<String, Object> orderResponse, Map<String, Object> cartResponse,
                                       List<Map<String, Object>> cartItems, BigDecimal total) {
        decreaseInventoryStock(cartItems);
        smsNotificationService.sendOrderConfirmationForOrder(
                getString(orderResponse, "orderUuid"), total, getString(orderResponse, "status"));
        clearOrderedCartItems(getLong(cartResponse, "cartId"), cartItems);
    }

    private void decreaseInventoryStock(List<Map<String, Object>> cartItems) {
        List<InventoryUpdateRequest.InventoryItem> inventoryItems = cartItems.stream()
                .map(item -> InventoryUpdateRequest.InventoryItem.builder()
                        .productId(requireLong(item, "productId", "Cart item product ID is missing"))
                        .variantId(getLong(item, "variantId"))
                        .quantity(requirePositiveQuantity(item))
                        .build())
                .toList();

        InventoryUpdateRequest inventoryRequest = InventoryUpdateRequest.builder()
                .requestId(UUID.randomUUID().toString())
                .items(inventoryItems)
                .build();

        try {
            Object response = rabbitTemplate.convertSendAndReceive(
                    inventoryExchange,
                    "inventory.stock.decrease.request",
                    inventoryRequest
            );

            if (response == null) {
                throw new BadRequestException("Inventory service did not return a response");
            }

            InventoryUpdateResponse updateResponse = objectMapper.convertValue(
                    parseLookupResponse(response),
                    InventoryUpdateResponse.class
            );

            if (!Boolean.TRUE.equals(updateResponse.getSuccess())) {
                String reason = buildInventoryFailureReason(updateResponse);
                log.warn("Customer order inventory decrease failed. requestId={}, reason={}",
                        inventoryRequest.getRequestId(), reason);
                throw new BadRequestException("Failed to update inventory: " + reason);
            }

            log.info("Inventory stock decreased for {} customer order item(s). requestId={}",
                    inventoryItems.size(), inventoryRequest.getRequestId());
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Inventory stock decrease failed for customer order. requestId={}",
                    inventoryRequest.getRequestId(), e);
            throw new BadRequestException("Failed to update inventory: " + e.getMessage());
        }
    }

    private Integer requirePositiveQuantity(Map<String, Object> item) {
        Integer quantity = getIntegerValue(item.get("quantity"));
        if (quantity == null || quantity <= 0) {
            throw new BadRequestException("Cart item quantity must be greater than zero");
        }
        return quantity;
    }

    private Long requireLong(Map<String, Object> map, String key, String message) {
        Long value = getLong(map, key);
        if (value == null) {
            throw new BadRequestException(message);
        }
        return value;
    }

    private String buildInventoryFailureReason(InventoryUpdateResponse response) {
        if (response == null) {
            return "No response from inventory module";
        }
        if (response.getFailedItems() != null && !response.getFailedItems().isEmpty()) {
            return String.join("; ", response.getFailedItems().stream()
                    .map(item -> String.format("product %d: %s", item.getProductId(), item.getReason()))
                    .toList());
        }
        return response.getErrorMessage() != null ? response.getErrorMessage() : "Unknown inventory error";
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderDetailResponse> getOrderById(String customerUuid) {
        log.info("Fetching customer orders for customerUuid: {}", customerUuid);

        Customer customer = customerRepository.findByUuid(customerUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with UUID: " + customerUuid));

        Map<String, Object> request = new HashMap<>();
        request.put("requestId", UUID.randomUUID().toString());
        request.put("customerId", customer.getId());

        Map<String, Object> response = sendOrderRequest("order.detail.lookup.request", request);
        if (!Boolean.TRUE.equals(response.get("success")) || !Boolean.TRUE.equals(response.get("found"))) {
            throw new ResourceNotFoundException("No orders found for customer UUID: " + customerUuid);
        }

        List<Map<String, Object>> orders = getMapList(response, "orders");
        if (!orders.isEmpty()) {
            return orders.stream()
                    .map(this::mapToOrderDetailResponse)
                    .toList();
        }

        return List.of(mapToOrderDetailResponse(response));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderPaymentStatusResponse getOrderPaymentStatus(String orderUuid, String customerUuid) {
        log.info("Fetching payment status for order {} of customer {}", orderUuid, customerUuid);

        Customer customer = customerRepository.findByUuid(customerUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with UUID: " + customerUuid));

        Map<String, Object> request = new HashMap<>();
        request.put("requestId", UUID.randomUUID().toString());
        request.put("orderUuid", orderUuid);
        request.put("customerId", customer.getId());

        Map<String, Object> response = sendOrderRequest("order.detail.lookup.request", request);
        if (!Boolean.TRUE.equals(response.get("success")) || !Boolean.TRUE.equals(response.get("found"))) {
            throw new ResourceNotFoundException("Order not found for UUID: " + orderUuid);
        }

        Map<String, Object> payment = getMap(response, "payment");
        return OrderPaymentStatusResponse.builder()
                .orderUuid(getString(response, "orderUuid"))
                .orderStatus(getString(response, "orderStatus"))
                .paymentStatus(getString(payment, "paymentStatus"))
                .build();
    }

    @Override
    public OrderPaymentStatusResponse updateOrderPaymentStatus(String orderUuid, String customerUuid,
                                                               UpdateOrderPaymentStatusRequest request) {
        log.info("Updating payment status for order {} of customer {} - reported status: {}",
                orderUuid, customerUuid, request.getPaymentStatus());

        String reportedStatus = request.getPaymentStatus().trim().toUpperCase();
        if ("PAID".equals(reportedStatus)) {
            throw new BadRequestException(
                    "Payment status PAID cannot be set by the client; it is confirmed via gateway verification");
        }

        Customer customer = customerRepository.findByUuid(customerUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with UUID: " + customerUuid));

        Map<String, Object> lookupRequest = new HashMap<>();
        lookupRequest.put("requestId", UUID.randomUUID().toString());
        lookupRequest.put("orderUuid", orderUuid);
        lookupRequest.put("customerId", customer.getId());

        Map<String, Object> orderResponse = sendOrderRequest("order.detail.lookup.request", lookupRequest);
        if (!Boolean.TRUE.equals(orderResponse.get("success")) || !Boolean.TRUE.equals(orderResponse.get("found"))) {
            throw new ResourceNotFoundException("Order not found for UUID: " + orderUuid);
        }

        String lookupStatus = getString(getMap(orderResponse, "payment"), "paymentStatus");
        Long orderId = getLong(orderResponse, "orderId");
        PaymentVerification verification = verifyPaymentStatusWithGateway(orderId);
        String verifiedStatus = verification != null ? verification.paymentStatus() : null;


        if (isTerminalStatus(verifiedStatus)) {
            assert verifiedStatus != null;
            if (!verifiedStatus.equalsIgnoreCase(lookupStatus)) {
                publishPaymentStatusUpdateEvent(orderId, verifiedStatus,
                        verification.gatewayTransactionId());
            }
        }

        return OrderPaymentStatusResponse.builder()
                .orderUuid(getString(orderResponse, "orderUuid"))
                .orderStatus(getString(orderResponse, "orderStatus"))
                .paymentStatus(resolvePaymentStatus(verifiedStatus, lookupStatus, reportedStatus))
                .build();
    }

    private boolean isTerminalStatus(String paymentStatus) {
        return "PAID".equalsIgnoreCase(paymentStatus) || "FAILED".equalsIgnoreCase(paymentStatus);
    }

    /**
     * Publishes the "payment.status.updated" event consumed by the order module. The consumer is
     * idempotent (it skips orders already carrying the same payment status), so re-publishing after a
     * verification is safe and never double-applies inventory, cart or SMS side effects.
     */
    private void publishPaymentStatusUpdateEvent(Long orderId, String paymentStatus, String gatewayTransactionId) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("orderId", orderId);
            event.put("paymentStatus", paymentStatus);
            event.put("gatewayTransactionId", gatewayTransactionId);

            rabbitTemplate.convertAndSend(paymentExchange, "payment.status.updated", event);
            log.info("Re-published payment.status.updated event after verification - orderId: {}, status: {}",
                    orderId, paymentStatus);
        } catch (Exception e) {
            log.error("Failed to re-publish payment status update for orderId: {}", orderId, e);
        }
    }

    /**
     * Resolves the payment status to report back, by trust order: the gateway verification result
     * wins outright; an already settled (PAID/FAILED) status from the order record is kept over the
     * client-reported one so a client can never downgrade a settled payment; otherwise the
     * client-reported status (e.g. UNPAID after an abandoned checkout) is echoed back.
     */
    private String resolvePaymentStatus(String verifiedStatus, String lookupStatus, String reportedStatus) {
        if (hasText(verifiedStatus)) {
            return verifiedStatus;
        }
        if ("PAID".equalsIgnoreCase(lookupStatus) || "FAILED".equalsIgnoreCase(lookupStatus)) {
            return lookupStatus;
        }
        return reportedStatus;
    }

    private record PaymentVerification(String paymentStatus, String gatewayTransactionId) {
    }

    /**
     * Asks the payment module (RabbitMQ RPC) to re-verify the order's latest payment request against
     * the gateway status API. Returns the verified payment status and gateway transaction ID, or null
     * when verification was not possible so the caller can fall back to the order lookup status.
     */
    private PaymentVerification verifyPaymentStatusWithGateway(Long orderId) {
        if (orderId == null) {
            return null;
        }

        Map<String, Object> verifyRequest = new HashMap<>();
        verifyRequest.put("requestId", UUID.randomUUID().toString());
        verifyRequest.put("orderId", orderId);

        try {
            Object response = rabbitTemplate.convertSendAndReceive(
                    paymentExchange, "payment.status.verify.request", verifyRequest);
            if (response == null) {
                log.warn("Payment status verification returned no response for orderId={}", orderId);
                return null;
            }

            Map<String, Object> verifyResponse = parseLookupResponse(response);
            if (!Boolean.TRUE.equals(verifyResponse.get("success"))) {
                log.warn("Payment status verification unsuccessful for orderId={}, error={}",
                        orderId, getString(verifyResponse, "error"));
                return null;
            }

            return new PaymentVerification(
                    getString(verifyResponse, "paymentStatus"),
                    getString(verifyResponse, "gatewayTransactionId"));
        } catch (Exception e) {
            log.error("Payment status verification failed via RabbitMQ. orderId={}", orderId, e);
            return null;
        }
    }

    @Override
    public CartResponseDTO createCart(String customerUuid, AddToCartRequestDTO request) {
        return null;
    }

    @Override
    public CartResponseDTO getCartById(String cartUuid, String customerUuid) {
        return null;
    }

    @Override
    public WishlistItemResponse createWishlistItem(String customerUuid, CreateWishlistItemRequest request) {
        log.info("Creating wishlist item for customer: {}, product: {}", customerUuid, request.getProductUuid());

        Customer customer = customerRepository.findByUuid(customerUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with UUID: " + customerUuid));

        boolean variantRequested = hasText(request.getVariantUuid());
        ProductLookupData product = lookupProductByUuid(request.getProductUuid(), request.getVariantUuid(), variantRequested);

        if (wishlistItemRepository.existsByCustomerIdAndProductIdAndIsAvailableTrue(customer.getId(), product.getId())) {
            throw new BadRequestException("Product already exists in customer wishlist");
        }

        WishlistItem wishlistItem = wishlistItemRepository.findByCustomerIdAndProductId(customer.getId(), product.getId())
                .orElseGet(() -> WishlistItem.builder()
                        .customerId(customer.getId())
                        .productId(product.getId())
                        .build());

        wishlistItem.setVariantId(variantRequested ? product.getVariantId() : null);
        wishlistItem.setPriority(request.getPriority());
        wishlistItem.setNotes(request.getNotes());
        wishlistItem.setTags(normalizeTags(request.getTags()));
        wishlistItem.setPriceSnapshot(product.getResolvedShowcasePrice());
        wishlistItem.setSalePriceSnapshot(product.getResolvedSalePrice());
        wishlistItem.setIsAvailable(true);
        wishlistItem.setSource(request.getSource());
        wishlistItem.setCapturedAt(java.time.LocalDateTime.now());

        WishlistItem savedItem = wishlistItemRepository.save(wishlistItem);
        log.info("Wishlist item created successfully with UUID: {}", savedItem.getUuid());

        return mapToWishlistItemResponse(savedItem, customer, product);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WishlistItemResponse> getWishlistItemsByCustomerUuid(String customerUuid) {
        log.info("Fetching wishlist items for customer: {}", customerUuid);

        Customer customer = customerRepository.findByUuid(customerUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with UUID: " + customerUuid));

        return wishlistItemRepository.findByCustomerIdAndIsAvailableTrue(customer.getId(), Pageable.unpaged())
                .getContent()
                .stream()
                .filter(item -> Boolean.TRUE.equals(item.getIsAvailable()))
                .map(item -> {
                    ProductLookupData product = lookupProductDetailsById(item.getProductId(), item.getVariantId());
                    return mapToWishlistItemResponse(item, customer, product);
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public WishlistItemResponse getWishlistItemByUuid(String wishlistItemUuid, String customerUuid) {
        log.info("Fetching wishlist item: {} for customer: {}", wishlistItemUuid, customerUuid);

        Customer customer = customerRepository.findByUuid(customerUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with UUID: " + customerUuid));

        WishlistItem wishlistItem = wishlistItemRepository.findByUuidAndIsAvailableTrue(wishlistItemUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Wishlist item not found with UUID: " + wishlistItemUuid));

        if (!customer.getId().equals(wishlistItem.getCustomerId())) {
            throw new ResourceNotFoundException("Wishlist item not found with UUID: " + wishlistItemUuid);
        }

        ProductLookupData product = lookupProductDetailsById(wishlistItem.getProductId(), wishlistItem.getVariantId());
        return mapToWishlistItemResponse(wishlistItem, customer, product);
    }

    @Override
    public void deleteWishlistItem(String wishlistItemUuid, String customerUuid) {
        log.info("Soft deleting wishlist item: {} for customer: {}", wishlistItemUuid, customerUuid);

        Customer customer = customerRepository.findByUuid(customerUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with UUID: " + customerUuid));

        WishlistItem wishlistItem = wishlistItemRepository.findByUuidAndIsAvailableTrue(wishlistItemUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Wishlist item not found with UUID: " + wishlistItemUuid));

        if (!customer.getId().equals(wishlistItem.getCustomerId())) {
            throw new ResourceNotFoundException("Wishlist item not found with UUID: " + wishlistItemUuid);
        }

        wishlistItem.setIsAvailable(false);
        wishlistItemRepository.save(wishlistItem);

        log.info("Wishlist item {} marked as unavailable for customer {}", wishlistItemUuid, customerUuid);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerDashboardCountsResponse getCustomerDashboardCounts(String customerUuid) {
        log.info("Fetching customer dashboard counts for customer: {}", customerUuid);

        Customer customer = customerRepository.findByUuid(customerUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with UUID: " + customerUuid));

        Map<String, Object> orderCounts = lookupCustomerOrderCounts(customer.getId());
        Long wishlistItemsCount = wishlistItemRepository.countByCustomerIdAndIsAvailableTrue(customer.getId());

        return CustomerDashboardCountsResponse.builder()
                .totalOrdersCount(getLongOrZero(orderCounts, "totalOrdersCount"))
                .pendingOrdersCount(getLongOrZero(orderCounts, "pendingOrdersCount"))
                .completedOrdersCount(getLongOrZero(orderCounts, "completedOrdersCount"))
                .wishlistItemsCount(wishlistItemsCount != null ? wishlistItemsCount : 0L)
                .build();
    }

    private Map<String, Object> lookupCustomerOrderCounts(Long customerId) {
        Map<String, Object> request = new HashMap<>();
        request.put("requestId", UUID.randomUUID().toString());
        request.put("customerId", customerId);

        try {
            Object response = rabbitTemplate.convertSendAndReceive(
                    orderExchange,
                    "customer.order.counts.lookup.request",
                    request
            );

            if (response == null) {
                throw new ResourceNotFoundException("Order count lookup service did not return a response");
            }

            Map<String, Object> responseMap = parseLookupResponse(response);
            if (!Boolean.TRUE.equals(responseMap.get("success"))) {
                String error = getString(responseMap, "error");
                throw new BadRequestException(error != null ? error : "Order count lookup failed");
            }

            return responseMap;
        } catch (ResourceNotFoundException | BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Customer order count lookup failed via RabbitMQ. customerId={}", customerId, e);
            throw new BadRequestException("Order count lookup failed: " + e.getMessage());
        }
    }

    private Map<String, Object> lookupCartItemsForOrder(String customerUuid, List<String> cartItemUuids) {
        CartItemsLookupRequest request = CartItemsLookupRequest.builder()
                .requestId(UUID.randomUUID().toString())
                .customerUuid(customerUuid)
                .selectedItemUuids(cartItemUuids)
                .build();

        try {
            Object response = rabbitTemplate.convertSendAndReceive(
                    cartExchange,
                    "cart.items.checkout.request",
                    request
            );

            if (response == null) {
                throw new ResourceNotFoundException("Cart lookup service did not return a response");
            }

            return parseLookupResponse(response);
        } catch (ResourceNotFoundException | BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Cart lookup failed via RabbitMQ. customerUuid={}", customerUuid, e);
            throw new BadRequestException("Cart lookup failed: " + e.getMessage());
        }
    }

    private Map<String, Object> sendOrderRequest(String routingKey, Map<String, Object> request) {
        try {
            Object response = rabbitTemplate.convertSendAndReceive(orderExchange, routingKey, request);
            if (response == null) {
                throw new ResourceNotFoundException("Order service did not return a response");
            }
            return parseLookupResponse(response);
        } catch (ResourceNotFoundException | BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Order request failed via RabbitMQ. routingKey={}, requestId={}", routingKey, request.get("requestId"), e);
            throw new BadRequestException("Order request failed: " + e.getMessage());
        }
    }

    private void clearOrderedCartItems(Long cartId, List<Map<String, Object>> cartItems) {
        if (cartId == null) {
            log.warn("Skipping cart clear because cartId was not returned by cart lookup");
            throw new BadRequestException("Order created but cart clear failed: cart ID was missing");
        }

        List<String> itemUuids = cartItems.stream()
                .map(item -> getString(item, "itemUuid"))
                .filter(this::hasText)
                .toList();

        if (itemUuids.isEmpty()) {
            log.warn("Skipping cart clear because no cart item UUIDs were returned for cartId={}", cartId);
            throw new BadRequestException("Order created but cart clear failed: cart item UUIDs were missing");
        }

        Map<String, Object> clearRequest = new HashMap<>();
        clearRequest.put("requestId", UUID.randomUUID().toString());
        clearRequest.put("cartId", cartId);
        clearRequest.put("itemUuids", itemUuids);

        try {
            Object response = sendCartClearRequest(clearRequest);

            if (response == null) {
                throw new BadRequestException("Cart clear service did not return a response");
            }

            Map<String, Object> clearResponse = parseLookupResponse(response);
            if (!Boolean.TRUE.equals(clearResponse.get("success"))) {
                String error = getString(clearResponse, "errorMessage");
                throw new BadRequestException(error != null ? error : "Cart clear failed");
            }

            log.info("Cleared {} cart item(s) after customer order creation. cartId={}",
                    getIntegerValue(clearResponse.get("itemsCleared")), cartId);
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Cart clear failed after customer order creation. cartId={}", cartId, e);
            throw new BadRequestException("Order created but cart clear failed: " + e.getMessage());
        }
    }

    private Object sendCartClearRequest(Map<String, Object> clearRequest) {
        Object response = rabbitTemplate.convertSendAndReceive(
                cartExchange,
                "cart.items.clear.request",
                clearRequest
        );

        if (response != null) {
            return response;
        }

        log.warn("No response via cart exchange route. Retrying cart clear via default exchange queue route. cartClearQueue={}",
                cartItemsClearRequestQueue);

        return rabbitTemplate.convertSendAndReceive(
                "",
                cartItemsClearRequestQueue,
                clearRequest
        );
    }

    private Map<String, Object> mapCartItemToOrderItemRequest(Map<String, Object> item) {
        Map<String, Object> mapped = new HashMap<>();
        mapped.put("productId", getLong(item, "productId"));
        mapped.put("variantId", getLong(item, "variantId"));
        mapped.put("productName", getString(item, "productTitle"));
        mapped.put("variantName", getString(item, "variantTitle"));
        mapped.put("quantity", getIntegerValue(item.get("quantity")));
        mapped.put("unitPrice", getBigDecimal(item, "unitPrice"));
        mapped.put("totalPrice", getBigDecimal(item, "totalPrice"));
        mapped.put("itemWeight", getBigDecimalOrZero(item, "itemWeight"));
        mapped.put("itemTotalWeight", getBigDecimalOrZero(item, "totalItemWeight"));
        return mapped;
    }

    private OrderDetailResponse mapToOrderDetailResponse(Map<String, Object> response) {
        return OrderDetailResponse.builder()
                .orderUuid(getString(response, "orderUuid"))
                .orderDate(parseDateTime(response.get("orderDate")))
                .deliveryDate(null)
                .amount(getBigDecimal(response, "total"))
                .type(getString(response, "orderFrom"))
                .status(getString(response, "orderStatus"))
                .items(getMapList(response, "items").stream()
                        .map(this::mapToOrderItemResponse)
                        .toList())
                .build();
    }

    private OrderDetailResponse.OrderItemResponse mapToOrderItemResponse(Map<String, Object> item) {
        return OrderDetailResponse.OrderItemResponse.builder()
                .productName(getString(item, "productName"))
                .variantName(getString(item, "variantName"))
                .unitPrice(getBigDecimal(item, "unitPrice"))
                .basePrice(getBigDecimal(item, "basePrice"))
                .quantity(getIntegerValue(item.get("quantity")))
                .totalPrice(getBigDecimal(item, "totalPrice"))
                .margin(getBigDecimal(item, "margin"))
                .isRefunded(getBooleanValue(item.get("isRefunded")))
                .build();
    }

    private ProductLookupData lookupProductByUuid(String productUuid, String variantUuid, boolean variantRequested) {
        Map<String, Object> request = new HashMap<>();
        request.put("requestId", UUID.randomUUID().toString());
        request.put("productUuid", productUuid);
        if (variantRequested) {
            request.put("variantUuid", variantUuid);
        }

        Map<String, Object> response = sendProductLookup("product.lookup.request", request);
        if (!Boolean.TRUE.equals(response.get("found"))) {
            String errorMessage = getString(response, "errorMessage");
            throw new ResourceNotFoundException(errorMessage != null ? errorMessage : "Product not found with UUID: " + productUuid);
        }

        return ProductLookupData.builder()
                .id(getLong(response, "productId"))
                .uuid(getString(response, "productUuid"))
                .title(getString(response, "productTitle"))
                .thumbnailUrl(getString(response, "thumbnailUrl"))
                .showcasePrice(getBigDecimal(response, "showcasePrice"))
                .salePrice(getBigDecimal(response, "salePrice"))
                .variantId(variantRequested ? getLong(response, "variantId") : null)
                .variantUuid(variantRequested ? getString(response, "variantUuid") : null)
                .variantAttributeSummary(variantRequested ? getString(response, "variantAttributeSummary") : null)
                .variantShowcasePrice(variantRequested ? getBigDecimal(response, "variantShowcasePrice") : null)
                .variantSalePrice(variantRequested ? getBigDecimal(response, "variantSalePrice") : null)
                .stockAvailable(getInteger(response))
                .isActive(true)
                .build();
    }

    private ProductLookupData lookupProductDetailsById(Long productId, Long variantId) {
        Map<String, Object> request = new HashMap<>();
        request.put("requestId", UUID.randomUUID().toString());
        request.put("productId", productId);
        if (variantId != null) {
            request.put("variantId", variantId);
        }

        Map<String, Object> response = sendProductLookup("product.details.lookup.request", request);
        if (!Boolean.TRUE.equals(response.get("found"))) {
            log.warn("Product details lookup failed for productId: {}, variantId: {}", productId, variantId);
            return null;
        }

        return ProductLookupData.builder()
                .id(getLong(response, "productId"))
                .uuid(getString(response, "productUuid"))
                .title(getString(response, "productTitle"))
                .thumbnailUrl(getString(response, "thumbnailUrl"))
                .showcasePrice(getBigDecimal(response, "showcasePrice"))
                .salePrice(getBigDecimal(response, "salePrice"))
                .variantId(getLong(response, "variantId"))
                .variantUuid(getString(response, "variantUuid"))
                .variantAttributeSummary(getString(response, "variantAttributeSummary"))
                .variantThumbnailUrl(getString(response, "variantThumbnailUrl"))
                .isActive(getBoolean(response))
                .build();
    }

    private Map<String, Object> sendProductLookup(String routingKey, Map<String, Object> request) {
        try {
            Object response = rabbitTemplate.convertSendAndReceive(productExchange, routingKey, request);
            if (response == null) {
                throw new ResourceNotFoundException("Product lookup service did not return a response");
            }
            return parseLookupResponse(response);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Product lookup failed via RabbitMQ. routingKey={}, requestId={}", routingKey, request.get("requestId"), e);
            throw new BadRequestException("Product lookup failed: " + e.getMessage());
        }
    }

    private Map<String, Object> parseLookupResponse(Object response) {
        try {
            if (response instanceof Message message) {
                return objectMapper.readValue(message.getBody(), new TypeReference<>() {});
            }
            if (response instanceof byte[] bytes) {
                return objectMapper.readValue(bytes, new TypeReference<>() {});
            }
            if (response instanceof String json) {
                return objectMapper.readValue(json, new TypeReference<>() {});
            }
            if (response instanceof Map<?, ?> map) {
                return objectMapper.convertValue(map, new TypeReference<>() {});
            }
            return objectMapper.convertValue(response, new TypeReference<>() {});
        } catch (Exception e) {
            throw new BadRequestException("Invalid RabbitMQ response: " + e.getMessage());
        }
    }

    private String normalizeTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return null;
        }

        String trimmedTags = tags.trim();
        try {
            Object parsedTags = objectMapper.readValue(trimmedTags, Object.class);
            return objectMapper.writeValueAsString(parsedTags);
        } catch (Exception ignored) {
            try {
                return objectMapper.writeValueAsString(List.of(trimmedTags));
            } catch (Exception e) {
                throw new BadRequestException("Invalid wishlist tags: " + e.getMessage());
            }
        }
    }

    private WishlistItemResponse mapToWishlistItemResponse(
            WishlistItem item,
            Customer customer,
            ProductLookupData product) {

        return WishlistItemResponse.builder()
                .id(item.getId())
                .uuid(item.getUuid())
                .customerId(item.getCustomerId())
                .customerUuid(customer != null ? customer.getUuid() : null)
                .productId(item.getProductId())
                .productUuid(product != null ? product.getUuid() : null)
                .productTitle(product != null ? product.getTitle() : null)
                .productThumbnailUrl(product != null ? product.getDisplayThumbnailUrl() : null)
                .variantId(item.getVariantId())
                .variantUuid(product != null ? product.getVariantUuid() : null)
                .variantAttributeSummary(product != null ? product.getVariantAttributeSummary() : null)
                .priority(item.getPriority())
                .notes(item.getNotes())
                .tags(item.getTags())
                .priceSnapshot(item.getPriceSnapshot())
                .salePriceSnapshot(item.getSalePriceSnapshot())
                .capturedAt(item.getCapturedAt())
                .isAvailable(item.getIsAvailable())
                .source(item.getSource())
                .dateCreated(item.getDateCreated())
                .dateUpdated(item.getDateUpdated())
                .build();
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private Long getLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(value.toString());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getMapList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(item -> objectMapper.convertValue(item, new TypeReference<Map<String, Object>>() {}))
                    .toList();
        }
        return (List<Map<String, Object>>) value;
    }

    private Map<String, Object> getMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return Map.of();
        }
        return objectMapper.convertValue(value, new TypeReference<Map<String, Object>>() {});
    }

    private Integer getIntegerValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.valueOf(value.toString());
    }

    private Long getLongOrZero(Map<String, Object> map, String key) {
        Long value = getLong(map, key);
        return value != null ? value : 0L;
    }

    private Integer getInteger(Map<String, Object> map) {
        Object value = map.get("stockAvailable");
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.valueOf(value.toString());
    }

    private BigDecimal getBigDecimal(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        return new BigDecimal(value.toString());
    }

    private BigDecimal getBigDecimalOrZero(Map<String, Object> map, String key) {
        BigDecimal value = getBigDecimal(map, key);
        return value != null ? value : BigDecimal.ZERO;
    }

    private LocalDateTime parseDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        return LocalDateTime.parse(value.toString());
    }

    private Boolean getBoolean(Map<String, Object> map) {
        Object value = map.get("isActive");
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.valueOf(value.toString());
    }

    private Boolean getBooleanValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.valueOf(value.toString());
    }

    @Data
    @Builder
    private static class ProductLookupData {
        private Long id;
        private String uuid;
        private String title;
        private String thumbnailUrl;
        private BigDecimal showcasePrice;
        private BigDecimal salePrice;
        private Long variantId;
        private String variantUuid;
        private String variantAttributeSummary;
        private String variantThumbnailUrl;
        private BigDecimal variantShowcasePrice;
        private BigDecimal variantSalePrice;
        private Integer stockAvailable;
        private Boolean isActive;

        private BigDecimal getResolvedShowcasePrice() {
            return variantShowcasePrice != null ? variantShowcasePrice : showcasePrice;
        }

        private BigDecimal getResolvedSalePrice() {
            return variantSalePrice != null ? variantSalePrice : salePrice;
        }

        private String getDisplayThumbnailUrl() {
            return variantThumbnailUrl != null ? variantThumbnailUrl : thumbnailUrl;
        }

        private boolean isAvailable() {
            if (stockAvailable != null) {
                return stockAvailable > 0;
            }
            return !Boolean.FALSE.equals(isActive);
        }
    }
}
