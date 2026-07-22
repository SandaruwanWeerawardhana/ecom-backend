package org.psint.beyosclothing.modules.pos.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.common.dto.PageResponse;
import org.psint.beyosclothing.core.exception.BadRequestException;
import org.psint.beyosclothing.core.exception.ResourceNotFoundException;
import org.psint.beyosclothing.modules.orders.dto.external.*;
import org.psint.beyosclothing.modules.orders.entity.OrderEntity;
import org.psint.beyosclothing.modules.orders.repository.OrderRepository;
import org.psint.beyosclothing.modules.orders.repository.projection.PosOrderListView;
import org.psint.beyosclothing.modules.orders.service.OrderCrossModuleLookupService;
import org.psint.beyosclothing.modules.pos.dto.request.PosDeliveryOrderRequest;
import org.psint.beyosclothing.modules.pos.dto.request.PosPlaceOrderRequest;
import org.psint.beyosclothing.modules.pos.dto.request.PosReceiptRequest;
import org.psint.beyosclothing.modules.pos.dto.response.PosOrderResponse;
import org.psint.beyosclothing.modules.pos.dto.response
        .PosReceiptDataResponse;
import org.psint.beyosclothing.modules.pos.dto.response.PosReceiptItemResponse;
import org.psint.beyosclothing.modules.pos.dto.response.PosReceiptResponse;
import org.psint.beyosclothing.modules.pos.entity.PosCartEntity;
import org.psint.beyosclothing.modules.pos.entity.PosCartItemEntity;
import org.psint.beyosclothing.modules.pos.entity.PosCustomerEntity;
import org.psint.beyosclothing.modules.pos.entity.PosProductCacheEntity;
import org.psint.beyosclothing.modules.pos.entity.PosReceiptEntity;
import org.psint.beyosclothing.modules.pos.entity.PosTerminalEntity;
import org.psint.beyosclothing.modules.pos.repository.PosCartItemRepository;
import org.psint.beyosclothing.modules.pos.repository.PosCartRepository;
import org.psint.beyosclothing.modules.pos.repository.PosCustomerRepository;
import org.psint.beyosclothing.modules.pos.repository.PosProductCacheRepository;
import org.psint.beyosclothing.modules.pos.repository.PosReceiptRepository;
import org.psint.beyosclothing.modules.pos.repository.PosTerminalRepository;
import org.psint.beyosclothing.modules.pos.service.PosCustomerLookupService;
import org.psint.beyosclothing.modules.pos.service.PosOrderService;
import org.psint.beyosclothing.modules.sms.service.OrderSmsNotificationService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PosOrderServiceImpl implements PosOrderService {

    private static final ZoneId SRI_LANKA_ZONE = ZoneId.of("Asia/Colombo");

    private final PosCartCacheService cartCacheService;
    private final PosCartRepository cartRepository;
    private final PosTerminalRepository terminalRepository;
    private final PosCartItemRepository cartItemRepository;
    private final PosProductCacheRepository productCacheRepository;
    private final PosCustomerRepository posCustomerRepository;
    private final PosReceiptRepository receiptRepository;
    private final OrderRepository orderRepository;
    private final OrderCrossModuleLookupService crossModuleLookupService;
    private final PosCustomerLookupService posCustomerLookupService;
    private final RabbitTemplate rabbitTemplate;
    private final OrderSmsNotificationService smsNotificationService;

    @Value("${app.rabbitmq.exchange.order:beyos.exchange.order}")
    private String orderExchange;

    @Override
    @Transactional
    public PosOrderResponse placeOrder(PosPlaceOrderRequest request, String terminalHeader, String cashierHeader) {
        log.info("Processing POS order placement for cart: {}", request.getCartUuid());

        try {
            // Find the active cart being checked out
            PosCartEntity cart = cartRepository.findByUuidAndIsActiveTrue(request.getCartUuid())
                    .orElseThrow(() -> new IllegalArgumentException("Cart not found: " + request.getCartUuid()));

            // Load the active items that make up this sale
            List<PosCartItemEntity> cartItems = cartItemRepository.findByCartIdAndIsActiveTrue(cart.getId());
            if (cartItems.isEmpty()) {
                throw new BadRequestException("Cannot checkout an empty cart: " + request.getCartUuid());
            }

            // Block checkout if any line exceeds the snapshotted stock
            validatePosStockAvailability(cartItems);

            cart.setIsActive(false);
            cartRepository.save(cart);

            // Decrease inventory for every sold item; throws and rolls back on failure
            decreasePosInventory(cartItems);

            // Keep the POS product cache snapshot consistent with the inventory decrease
            syncPosCacheStockAfterSale(cartItems);

            // Clear Redis cache for this terminal to reset for next customer
            String terminalUuid = terminalRepository.findById(cart.getTerminalId())
                    .map(PosTerminalEntity::getUuid)
                    .orElse(null);
            if (terminalUuid != null) {
                try {
                    cartCacheService.removeCacheForTerminal(terminalUuid);
                    log.info("Cleared cache for terminal {} after checkout", terminalUuid);
                } catch (Exception e) {
                    log.warn("Failed to clear cache for terminal {} after checkout: {}", terminalUuid, e.getMessage());
                }
            }

            return mapPosCartToOrderResponse(cart);

        } catch (BadRequestException | ResourceNotFoundException e) {
            log.error("POS order placement validation failed: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Failed to place POS order: {}", e.getMessage(), e);
            throw new RuntimeException("Order placement failed: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public PosOrderResponse placeDeliveryOrder(PosDeliveryOrderRequest request, String customerUuid, String guestToken) {
        log.info("Processing POS delivery order - Customer UUID: {}, Guest Token: {}",
                customerUuid, guestToken != null ? "***" : null);

        try {
            // ===== STEP 1: Parse cart item UUIDs and fetch items from POS cart =====
            List<String> itemUuids = request.getCartItemUuids() != null
                    ? request.getCartItemUuids().stream()
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList()
                    : List.of();

            if (itemUuids.isEmpty()) {
                throw new BadRequestException("No cart items selected");
            }

            List<PosCartItemEntity> cartItems = new ArrayList<>();
            for (String itemUuid : itemUuids) {
                PosCartItemEntity item = cartItemRepository.findByUuid(itemUuid)
                        .orElseThrow(() -> new BadRequestException("Cart item not found: " + itemUuid));
                cartItems.add(item);
            }

            Long cartId = cartItems.getFirst().getCartId();
            PosCartEntity cart = cartRepository.findById(cartId)
                    .orElseThrow(() -> new BadRequestException("Cart not found for id: " + cartId));

            validateCartItemsBelongToCart(cartItems, cartId);

            if (!Boolean.TRUE.equals(cart.getIsActive())) {
                throw new BadRequestException("Cart is not active");
            }

            String orderUuid = buildStablePosDeliveryOrderUuid(cart);
            Optional<OrderEntity> existingOrder = findExistingPosDeliveryOrder(orderUuid, cartId);
            if (existingOrder.isPresent()) {
                log.warn("POS delivery order already exists for cart {}. Returning existing order {}",
                        cartId, existingOrder.get().getOrderNumber());
                return completeExistingPosDeliveryOrder(cart, existingOrder.get());
            }

            log.info("POS cart items retrieved - {} items, Cart ID: {}", cartItems.size(), cartId);

            // ===== STEP 2: Validate Stock Availability =====
            validatePosStockAvailability(cartItems);

            // ===== STEP 3: Determine source and fetch customer details =====
            String source = request.getSource() != null ? request.getSource() : "POS";
            boolean isOnlineSource = "ONLINE".equalsIgnoreCase(source);
            String cartCustomerType = normalizeCartCustomerType(cart.getCustomerType());
            boolean useOnlineCustomer = isOnlineSource || "ONLINE".equals(cartCustomerType);
            String headerCustomerUuid = firstNonBlank(customerUuid, null);
            String requestCustomerUuid = firstNonBlank(request.getCustomerUuid(), null);

            CustomerLookupResponse customer = null;
            PosCustomerEntity posCustomer = null;

            if (useOnlineCustomer) {
                // ONLINE source: look up customer from Customer module via RabbitMQ
                String onlineCustomerUuid = firstNonBlank(headerCustomerUuid, requestCustomerUuid);
                if (isBlank(onlineCustomerUuid) && "ONLINE".equals(cartCustomerType)) {
                    onlineCustomerUuid = posCustomerLookupService.getCustomerUuidById(cart.getCustomerId());
                }
                if (!isBlank(onlineCustomerUuid)) {
                    customer = crossModuleLookupService.lookupCustomer(onlineCustomerUuid);
                    if (customer == null || !customer.getFound()) {
                        throw new ResourceNotFoundException("Customer", "uuid", onlineCustomerUuid);
                    }
                }
            } else {
                // POS source: look up customer from local POS customer table
                String posCustomerUuid = firstNonBlank(requestCustomerUuid, headerCustomerUuid);
                if (!isBlank(posCustomerUuid)) {
                    posCustomer = posCustomerRepository.findByUuid(posCustomerUuid)
                            .orElseThrow(() -> new ResourceNotFoundException("POS Customer", "uuid", posCustomerUuid));
                } else if ("POS".equals(cartCustomerType) && cart.getCustomerId() != null) {
                    posCustomer = posCustomerRepository.findById(cart.getCustomerId())
                            .orElseThrow(() -> new ResourceNotFoundException("POS Customer", "id",
                                    cart.getCustomerId().toString()));
                }
            }

            // ===== STEP 4: Fetch and Validate Delivery Address =====
            CustomerAddressResponse address = null;

            if (useOnlineCustomer) {
                Long deliveryAddressId = normalizeOptionalId(request.getDeliveryAddressId());
                Long onlineCustomerId = customer != null ? customer.getCustomerId() : null;
                if (deliveryAddressId == null && onlineCustomerId == null) {
                    throw new BadRequestException("A delivery address is required: provide a deliveryAddressId "
                            + "or an online customer with a default address");
                }

                address = crossModuleLookupService.lookupCustomerAddress(onlineCustomerId, deliveryAddressId);
                if (address == null || !address.getFound()) {
                    if (deliveryAddressId != null) {
                        throw new ResourceNotFoundException("Address", "id", deliveryAddressId.toString());
                    }
                    throw new BadRequestException("No default delivery address found for this customer. "
                            + "Provide a deliveryAddressId or set a default address for the customer.");
                }
            }

            // ===== STEP 5: Fetch Payment Method Details =====
            Long paymentMethodId = normalizeOptionalId(request.getPaymentMethodId());
            PaymentMethodLookupResponse paymentMethod = null;
            if (paymentMethodId != null) {
                paymentMethod = crossModuleLookupService.lookupPaymentMethod(paymentMethodId);
                if (paymentMethod == null || !Boolean.TRUE.equals(paymentMethod.getFound())
                        || !Boolean.TRUE.equals(paymentMethod.getIsActive())) {
                    throw new BadRequestException("Payment method not found or inactive: " + paymentMethodId);
                }
            } else {
                log.info("No valid payment method ID provided (raw: {}) - order will be created without a payment request",
                        request.getPaymentMethodId());
            }

            // ===== STEP 6: Calculate Shipping Cost (optional - only if courierUuid is provided) =====
            String customerType;
            if (useOnlineCustomer && customer != null) {
                customerType = customer.getCustomerType() != null ? customer.getCustomerType() : "CUSTOMER";
            } else {
                customerType = "CUSTOMER";
            }

            ShippingCostResponse shippingCost = null;
            BigDecimal shippingFee = BigDecimal.ZERO;
            String courierUuid = request.getCourierUuid();
            Long courierId = normalizeOptionalId(request.getCourierId());
            if (isBlank(courierUuid) && courierId != null) {
                throw new BadRequestException("courierUuid is required when courierId is provided");
            }

            if (courierUuid != null && !courierUuid.isBlank()) {
                shippingCost = crossModuleLookupService.calculateShippingCost(
                        courierUuid,
                        BigDecimal.ZERO,
                        paymentMethodId,
                        customerType
                );

                if (shippingCost == null || !Boolean.TRUE.equals(shippingCost.getFound())) {
                    throw new BadRequestException("Unable to calculate shipping cost for courier UUID: "
                            + courierUuid + ". Please verify the courier is active and available.");
                }

                log.info("Shipping cost calculated - Courier: {}, Cost: {}",
                        shippingCost.getCourierName(), shippingCost.getShippingCost());
            } else {
                log.info("No courier UUID provided - shipping cost will be zero");
            }

            // ===== STEP 7: Calculate Totals =====
            BigDecimal subtotal = cartItems.stream()
                    .map(PosCartItemEntity::getTotalPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal promoDiscount = BigDecimal.ZERO;
            if (shippingCost != null && Boolean.TRUE.equals(shippingCost.getIsFree())) {
                shippingFee = BigDecimal.ZERO;
            } else if (shippingCost != null) {
                shippingFee = shippingCost.getShippingCost();
            }
            BigDecimal total = subtotal.subtract(promoDiscount).add(shippingFee);

            log.info("POS Order calculations - Subtotal: {}, Shipping: {}, Total: {}",
                    subtotal, shippingFee, total);

            // ===== STEP 8: Build items list for RabbitMQ request =====
            List<Map<String, Object>> itemsList = buildPosOrderItemsList(cartItems);

            // ===== STEP 9: Resolve and validate the delivery recipient & address =====
            DeliveryRecipient recipient = resolveDeliveryRecipient(address, posCustomer);

            Map<String, Object> shippingAddressMap = new HashMap<>();
            shippingAddressMap.put("line1", recipient.addressLine1());
            shippingAddressMap.put("line2", recipient.addressLine2());
            shippingAddressMap.put("city", recipient.city());
            shippingAddressMap.put("province", recipient.province());
            shippingAddressMap.put("postalCode", recipient.postalCode());

            // ===== STEP 10: Decrease Inventory Stock =====
            decreasePosInventory(cartItems);

            // Keep the POS product cache snapshot consistent with the inventory decrease
            syncPosCacheStockAfterSale(cartItems);

            // ===== STEP 11: Send order creation request via RabbitMQ =====
            Map<String, Object> orderRequest = new HashMap<>();
            orderRequest.put("source", source);
            orderRequest.put("orderUuid", orderUuid);
            if (useOnlineCustomer) {
                orderRequest.put("customerId", customer != null ? customer.getCustomerId() : null);
            } else {
                orderRequest.put("customerId", posCustomer.getId());
            }
            orderRequest.put("cartId", cartId);
            orderRequest.put("customerName", recipient.fullName());
            orderRequest.put("customerPhone", recipient.phone());
            orderRequest.put("customerEmail", recipient.email());
            orderRequest.put("paymentMethod", paymentMethod != null ? paymentMethod.getMethodName() : null);
            orderRequest.put("paymentMethodId", paymentMethod != null ? paymentMethod.getMethodId() : null);
            orderRequest.put("posTerminalId", cart.getTerminalId());
            orderRequest.put("posCashierId", cart.getCashierId());
            orderRequest.put("subtotal", subtotal);
            orderRequest.put("deliveryCharges", shippingFee);
            orderRequest.put("promoDiscount", promoDiscount);
            orderRequest.put("total", total);
            orderRequest.put("notes", request.getCustomerNotes());
            orderRequest.put("shippingWeight", BigDecimal.ZERO);
            orderRequest.put("shippingBreakdown", shippingCost != null ? shippingCost.getBreakdown() : null);
            orderRequest.put("shippingPayer", "CUSTOMER");
            orderRequest.put("shippingAddress", shippingAddressMap);
            orderRequest.put("items", itemsList);

            log.info("Sending POS order creation request via RabbitMQ - Exchange: {}, RoutingKey: order.create.request",
                    orderExchange);

            Map<String, Object> orderResponse = sendOrderCreationRequest(orderRequest);

            if (orderResponse == null || !Boolean.TRUE.equals(orderResponse.get("success"))) {
                Optional<OrderEntity> completedOrder = awaitCreatedPosOrder(orderUuid, cartId);
                if (completedOrder.isPresent()) {
                    log.warn("Order module reply was missing/failed, but POS order already exists for cart {}. "
                            + "Continuing with existing order {}", cartId, completedOrder.get().getOrderNumber());
                    orderResponse = buildOrderResponse(completedOrder.get());
                } else {
                    String errorMsg = orderResponse != null
                            ? (String) orderResponse.get("error") : "No response from order module";
                    throw new RuntimeException("Order creation failed: " + errorMsg);
                }
            }

            Long orderId = toLong(orderResponse.get("orderId"));
            String createdOrderUuid = firstNonBlank((String) orderResponse.get("orderUuid"), orderUuid);
            String orderNumber = (String) orderResponse.get("orderNumber");
            LocalDateTime createdAt = parseOrderDate((String) orderResponse.get("orderDate"));

            log.info("POS Order created via RabbitMQ - Order ID: {}, Number: {}, UUID: {}",
                    orderId, orderNumber, createdOrderUuid);

            notifyOrderConfirmationSafely(createdOrderUuid, orderNumber, total);

            ShipmentCreationResponse shipment = createPosShipment(orderId, shippingFee, shippingCost, paymentMethodId);
            if (shipment != null && Boolean.TRUE.equals(shipment.getSuccess())) {
                log.info("Shipment created - Shipment ID: {}", shipment.getShipmentId());
            } else {
                log.warn("Shipment creation failed - Order will proceed without shipment link");
            }

            // ===== STEP 13: Create Payment Request =====
            PaymentRequestCreationResponse paymentRequest = createPosPaymentRequest(
                    createdOrderUuid, orderId, orderNumber, paymentMethod, total);

            String redirectUrl = null;
            if (paymentRequest != null && Boolean.TRUE.equals(paymentRequest.getSuccess())) {
                redirectUrl = paymentRequest.getRedirectUrl();
                log.info("Payment request created - Status: {}, Redirect URL: {}",
                        paymentRequest.getStatus(), redirectUrl != null ? "Yes" : "No");
            }

            // ===== STEP 14: Mark POS Cart as Inactive and clear the terminal cache
            String terminalCode = deactivateCartSafely(cart);

            // ===== STEP 15: Build Response
            PosOrderResponse response = buildPosDeliveryOrderResponse(
                    createdOrderUuid, orderNumber, "PENDING", terminalCode, total, redirectUrl, createdAt);

            log.info("POS delivery order placement completed successfully - Order Number: {}", orderNumber);
            return response;

        } catch (BadRequestException | ResourceNotFoundException e) {
            log.error("POS delivery order placement validation failed: {}", e.getMessage());
            throw e;
        }
    }

    @Override
    public PosOrderResponse getOrderByUuid(String orderUuid, String terminalHeader, String cashierHeader) {
        return PosOrderResponse.builder()
                .build();
    }

    @Override
    public PageResponse<PosOrderResponse> listOrders(Optional<LocalDate> dateFrom, Optional<LocalDate> dateTo, Pageable pageable) {
        LocalDateTime startDateTime = dateFrom.map(LocalDate::atStartOfDay)
                .orElse(LocalDate.EPOCH.atStartOfDay());
        LocalDateTime endDateExclusive = dateTo.map(date -> date.plusDays(1).atStartOfDay())
                .orElseGet(() -> LocalDate.now(SRI_LANKA_ZONE).plusDays(1).atStartOfDay());
        Page<PosCartEntity> cartsPage = cartRepository.findCompletedCarts(startDateTime, endDateExclusive, pageable);

        List<Long> cartIds = cartsPage.getContent().stream()
                .map(PosCartEntity::getId)
                .toList();


        Map<Long, PosOrderListView> orderViewByCartId = new HashMap<>();
        if (!cartIds.isEmpty()) {
            for (PosOrderListView orderView
                    : orderRepository.findPosOrderViewsByCartIds(cartIds, OrderEntity.OrderSource.POS)) {
                orderViewByCartId.putIfAbsent(orderView.getCartId(), orderView);
            }
        }

        List<PosOrderResponse> content = cartsPage.getContent().stream()
                .map(cart -> mapCompletedCartToOrderResponse(cart, orderViewByCartId.get(cart.getId())))
                .toList();

        return PageResponse.<PosOrderResponse>builder()
                .content(content)
                .pageNumber(cartsPage.getNumber())
                .pageSize(cartsPage.getSize())
                .totalElements(cartsPage.getTotalElements())
                .totalPages(cartsPage.getTotalPages())
                .last(cartsPage.isLast())
                .first(cartsPage.isFirst())
                .empty(cartsPage.isEmpty())
                .build();
    }
    @Override
    public PosReceiptDataResponse getReceiptForOrder(String orderUuid) {
        OrderEntity order = orderRepository.findByUuid(orderUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "uuid", orderUuid));

        List<PosCartItemEntity> cartItems = order.getCartId() != null
                ? cartItemRepository.findByCartIdAndIsActiveTrue(order.getCartId())
                : List.of();

        List<PosReceiptItemResponse> receiptItems = new ArrayList<>();
        int lineNo = 1;
        for (PosCartItemEntity item : cartItems) {
            PosProductCacheEntity product = productCacheRepository.findByProductId(item.getProductId()).orElse(null);
            receiptItems.add(PosReceiptItemResponse.builder()
                    .lineNo(lineNo++)
                    .product(product != null ? product.getTitle() : "Product #" + item.getProductId())
                    .sku(product != null ? product.getSku() : null)
                    .price(item.getUnitPrice())
                    .qty(item.getQuantity())
                    .subtotal(item.getTotalPrice())
                    .build());
        }

        return PosReceiptDataResponse.builder()
                .billNo(order.getOrderNumber())
                .date(order.getCreatedAt())
                .items(receiptItems)
                .subtotal(order.getSubtotal())
                .grandTotal(order.getTotal())
                .build();
    }

    @Override
    @Transactional
    public PosReceiptResponse createReceipt(PosReceiptRequest request) {
        String orderUuid = request.getOrderUuid().trim();
        log.info("Creating POS receipt for order UUID: {}", orderUuid);

        PosCartEntity cart = cartRepository.findByUuid(orderUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "uuid", orderUuid));
        Long orderId = cart.getId();

        LocalDateTime printedAt = request.getPrintedAt() != null ? request.getPrintedAt() : LocalDateTime.now();
        PosReceiptEntity receipt = PosReceiptEntity.builder()
                .orderId(orderId)
                .receiptNumber(request.getReceiptNumber())
                .printCount(1)
                .printedAt(printedAt)
                .build();

        PosReceiptEntity saved = receiptRepository.save(receipt);
        return mapReceiptToResponse(saved, orderUuid);
    }

    // ===== PRIVATE HELPER METHODS =====

    private Optional<OrderEntity> findExistingPosOrder(Long cartId) {
        if (cartId == null) {
            return Optional.empty();
        }
        return orderRepository.findFirstBySourceAndCartIdOrderByCreatedAtDesc(OrderEntity.OrderSource.POS, cartId);
    }

    /**
     * Finds an existing POS delivery order for the given cart ID, using the stable delivery
     * order UUID if available. This is used to handle retried requests that may have been
     * received multiple times due to network issues.
     */

    private Optional<OrderEntity> findExistingPosDeliveryOrder(String orderUuid, Long cartId) {
        return orderRepository.findByUuid(orderUuid)
                .or(() -> findExistingPosOrder(cartId));
    }

    /**
     * Waits for an order to be created for the given cart, using the provided order UUID as a
     * hint. This is used after placing an order to allow time for the order module to respond
     * (which may take a few hundred milliseconds under load). If the order is not found, this
     * falls back to looking up the most recent POS order for the cart, which handles the case
     * where the order UUID was not provided or was invalid.
     */

    private static final int CREATED_ORDER_LOOKUP_ATTEMPTS = 10;
    private static final long CREATED_ORDER_LOOKUP_DELAY_MS = 500L;


    private Optional<OrderEntity> awaitCreatedPosOrder(String orderUuid, Long cartId) {
        for (int attempt = 1; attempt <= CREATED_ORDER_LOOKUP_ATTEMPTS; attempt++) {
            Optional<OrderEntity> order = Optional.empty();
            if (!isBlank(orderUuid)) {
                order = orderRepository.findByUuid(orderUuid);
            }
            if (order.isEmpty()) {
                order = findExistingPosOrder(cartId);
            }
            if (order.isPresent()) {
                return order;
            }
            if (attempt < CREATED_ORDER_LOOKUP_ATTEMPTS) {
                try {
                    Thread.sleep(CREATED_ORDER_LOOKUP_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return Optional.empty();
    }

    private String buildStablePosDeliveryOrderUuid(PosCartEntity cart) {
        String seed = "POS_DELIVERY_ORDER:" + firstNonBlank(cart.getUuid(), String.valueOf(cart.getId()));
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private Map<String, Object> buildOrderResponse(OrderEntity order) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("orderId", order.getId());
        response.put("orderNumber", order.getOrderNumber());
        response.put("orderUuid", order.getUuid());
        response.put("orderDate", order.getCreatedAt() != null ? order.getCreatedAt().toString() : null);
        response.put("status", order.getStatus() != null ? order.getStatus().name() : null);
        return response;
    }

    private PosOrderResponse completeExistingPosDeliveryOrder(PosCartEntity cart, OrderEntity order) {
        if (Boolean.TRUE.equals(cart.getIsActive())) {
            cart.setIsActive(false);
            cartRepository.save(cart);
        }
        String terminalCode = clearTerminalCacheAndResolveCode(cart.getTerminalId());
        String status = order.getStatus() != null ? order.getStatus().name() : "PENDING";
        return buildPosDeliveryOrderResponse(
                order.getUuid(), order.getOrderNumber(), status, terminalCode,
                order.getTotal(), null, order.getCreatedAt());
    }

    /**
     * Sends the order creation request over RabbitMQ RPC. A broker or reply-channel failure is
     * returned as null instead of thrown, so the caller falls into the same recovery path as a
     * timed-out reply and checks whether the order module created the order anyway.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> sendOrderCreationRequest(Map<String, Object> orderRequest) {
        try {
            return (Map<String, Object>) rabbitTemplate.convertSendAndReceive(
                    orderExchange,
                    "order.create.request",
                    orderRequest
            );
        } catch (Exception e) {
            log.warn("Order creation RPC failed - will check whether the order was still created: {}",
                    e.getMessage());
            return null;
        }
    }

    /**
     * Parses the creation timestamp returned by the order module. The order is already committed
     * when this runs, so an unparseable value must not fail the request.
     */
    private LocalDateTime parseOrderDate(String orderDate) {
        if (isBlank(orderDate)) {
            return LocalDateTime.now(SRI_LANKA_ZONE);
        }
        try {
            return LocalDateTime.parse(orderDate);
        } catch (DateTimeParseException e) {
            log.warn("Could not parse order date '{}' - falling back to current time", orderDate);
            return LocalDateTime.now(SRI_LANKA_ZONE);
        }
    }

    private void notifyOrderConfirmationSafely(String orderUuid, String orderNumber, BigDecimal total) {
        try {
            smsNotificationService.sendOrderConfirmationForOrder(orderUuid, total, "PENDING");
        } catch (Exception e) {
            log.warn("Failed to queue confirmation SMS for order {}: {}", orderNumber, e.getMessage());
        }
    }

    /**
     * Deactivates the checked-out cart and clears its terminal cache. Called only after the order
     * is committed in the order module, so failures are logged instead of thrown: a still-active
     * cart is recovered by the duplicate-order check on the next request, while an error response
     * here would invite a retry of an already placed order.
     */
    private String deactivateCartSafely(PosCartEntity cart) {
        try {
            cart.setIsActive(false);
            cartRepository.save(cart);
        } catch (Exception e) {
            log.warn("Failed to deactivate POS cart {} after delivery order checkout: {}",
                    cart.getUuid(), e.getMessage());
        }
        return clearTerminalCacheAndResolveCode(cart.getTerminalId());
    }

    /**
     * Resolves the terminal code and clears the terminal's cart cache. Fail-soft: the cache is a
     * convenience copy and this runs after the order exists, so a Redis or lookup failure only
     * costs the terminal code in the response, never the request itself.
     */
    private String clearTerminalCacheAndResolveCode(Long terminalId) {
        try {
            Optional<PosTerminalEntity> terminal = terminalRepository.findById(terminalId);
            terminal.map(PosTerminalEntity::getUuid).ifPresent(terminalUuid -> {
                cartCacheService.removeCacheForTerminal(terminalUuid);
                log.info("Cleared cache terminal {} after delivery order checkout", terminalUuid);
            });
            return terminal.map(PosTerminalEntity::getCode).orElse(null);
        } catch (Exception e) {
            log.warn("Failed to clear terminal cache after delivery order checkout: {}", e.getMessage());
            return null;
        }
    }

    private PosOrderResponse buildPosDeliveryOrderResponse(String orderUuid, String orderNumber, String status,
                                                           String terminalCode, BigDecimal total,
                                                           String redirectUrl, LocalDateTime createdAt) {
        return PosOrderResponse.builder()
                .orderUuid(orderUuid)
                .orderNumber(orderNumber)
                .status(status)
                .total(total)
                .paymentRedirectUrl(redirectUrl)
                .createdAt(createdAt)
                .receiptNumber(orderNumber)
                .terminalCode(terminalCode)
                .build();
    }
    /**
     * Immutable delivery destination used to build the order-creation request. Its fields mirror
     * the order module's order_shipping_address columns.
     */
    private record DeliveryRecipient(
            String fullName, String phone, String email,
            String addressLine1, String addressLine2, String city,
            String province, String postalCode) {
    }

    /**
     * Resolves the delivery recipient and address from either the online customer address or the
     * local POS customer, and validates the fields the order module persists as NOT NULL. Throws a
     * BadRequestException (clean 400) when the destination is missing or incomplete, instead of
     * letting the downstream order-creation transaction fail with an opaque "No response" error.
     */
    private DeliveryRecipient resolveDeliveryRecipient(CustomerAddressResponse address, PosCustomerEntity posCustomer) {
        DeliveryRecipient recipient;
        if (address != null) {
            recipient = new DeliveryRecipient(
                    address.getFullName(), address.getPhoneNumber(), address.getEmail(),
                    address.getAddressLine1(), address.getAddressLine2(), address.getCity(),
                    address.getProvince(), address.getPostalCode());
        } else if (posCustomer != null) {
            recipient = new DeliveryRecipient(
                    posCustomer.getFullName(), posCustomer.getPhone(), null,
                    posCustomer.getAddress(), null, posCustomer.getCity(),
                    posCustomer.getProvince(), posCustomer.getZipCode());
        } else {
            throw new BadRequestException(
                    "A customer with a delivery address is required to place a POS delivery order");
        }

        List<String> missingFields = new ArrayList<>();
        if (isBlank(recipient.fullName())) {
            missingFields.add("customer name");
        }
        if (isBlank(recipient.phone())) {
            missingFields.add("customer phone");
        }
        if (isBlank(recipient.addressLine1())) {
            missingFields.add("address");
        }
        if (isBlank(recipient.city())) {
            missingFields.add("city");
        }
        if (!missingFields.isEmpty()) {
            throw new BadRequestException("Delivery address is incomplete - missing: "
                    + String.join(", ", missingFields));
        }

        return recipient;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String firstNonBlank(String first, String second) {
        if (!isBlank(first)) {
            return first.trim();
        }
        return !isBlank(second) ? second.trim() : null;
    }

    private String normalizeCartCustomerType(String customerType) {
        if (customerType == null || customerType.isBlank()) {
            return "WALK_IN";
        }
        return customerType.trim().toUpperCase(Locale.ROOT);
    }

    private void validateCartItemsBelongToCart(List<PosCartItemEntity> cartItems, Long cartId) {
        for (PosCartItemEntity item : cartItems) {
            if (!Objects.equals(item.getCartId(), cartId)) {
                throw new BadRequestException("Selected cart items must belong to the same POS cart");
            }
            if (!Boolean.TRUE.equals(item.getIsActive())) {
                throw new BadRequestException("Cart item is not active: " + item.getUuid());
            }
        }
    }

    private PosReceiptResponse mapReceiptToResponse(PosReceiptEntity receipt, String orderUuid) {
        return PosReceiptResponse.builder()
                .uuid(receipt.getUuid())
                .orderId(receipt.getOrderId())
                .orderUuid(orderUuid)
                .receiptNumber(receipt.getReceiptNumber())
                .printCount(receipt.getPrintCount())
                .printedAt(receipt.getPrintedAt())
                .createdAt(receipt.getCreatedAt())
                .build();
    }

    private PosOrderResponse mapPosCartToOrderResponse(PosCartEntity cart) {
        return PosOrderResponse.builder()
                .status("COMPLETE")
                .createdAt(cart.getCreatedAt())
                .total(cart.getTotal())
                .subtotal(cart.getSubtotal())
                .cartId(cart.getId())
                .cartUuid(cart.getUuid())
                .cartTerminalId(cart.getTerminalId())
                .cartCashierId(cart.getCashierId())
                .cartCustomerId(cart.getCustomerId())
                .cartCustomerType(cart.getCustomerType())
                .cartIsActive(cart.getIsActive())
                .cartIsDraft(cart.getIsDraft())
                .cartSubtotal(cart.getSubtotal())
                .cartTaxAmount(cart.getTaxAmount())
                .cartTaxPercentage(cart.getTaxPercentage())
                .cartDiscountAmount(cart.getDiscountAmount())
                .cartTotal(cart.getTotal())
                .cartCreatedAt(cart.getUpdatedAt())
                .cartUpdatedAt(cart.getUpdatedAt())
                .build();
    }

    /**
     * Maps a completed POS cart to a list response. When the cart produced an order row (delivery
     * orders), the order's identifiers, statuses and shipping figures enrich the response; walk-in
     * checkouts have no order row and are reported as completed in-store sales from cart data alone.
     */
    private PosOrderResponse mapCompletedCartToOrderResponse(PosCartEntity cart, PosOrderListView order) {
        PosOrderResponse.PosOrderResponseBuilder<?, ?> builder = PosOrderResponse.builder()
                .cartId(cart.getId())
                .cartUuid(cart.getUuid())
                .cartTerminalId(cart.getTerminalId())
                .cartCashierId(cart.getCashierId())
                .cartCustomerId(cart.getCustomerId())
                .cartCustomerType(cart.getCustomerType() != null ? cart.getCustomerType() : "POS")
                .cartIsActive(cart.getIsActive())
                .cartIsDraft(cart.getIsDraft())
                .cartSubtotal(cart.getSubtotal())
                .cartTaxAmount(cart.getTaxAmount())
                .cartTaxPercentage(cart.getTaxPercentage())
                .cartDiscountAmount(cart.getDiscountAmount())
                .cartTotal(cart.getTotal())
                .cartCreatedAt(cart.getUpdatedAt())
                .cartUpdatedAt(cart.getUpdatedAt());

        if (order != null) {
            builder.orderUuid(order.getUuid())
                    .orderNumber(order.getOrderNumber())
                    .status(order.getStatus() != null ? order.getStatus().name() : null)
                    .paymentStatus(order.getPaymentStatus() != null ? order.getPaymentStatus().name() : null)
                    .subtotal(order.getSubtotal())
                    .discountTotal(order.getDiscountTotal())
                    .shippingCost(order.getShippingCost())
                    .total(order.getTotal())
                    .createdAt(order.getCreatedAt())
                    .receiptNumber(order.getOrderNumber());
        } else {
            // Walk-in checkouts have no order row, so the cart uuid serves as the order identifier
            builder.orderUuid(cart.getUuid())
                    .status("COMPLETE")
                    .subtotal(cart.getSubtotal())
                    .discountTotal(cart.getDiscountAmount())
                    .total(cart.getTotal())
                    .createdAt(cart.getUpdatedAt());
        }
        return builder.build();
    }

    private void validatePosStockAvailability(List<PosCartItemEntity> items) {
        List<String> outOfStockItems = new ArrayList<>();
        for (PosCartItemEntity item : items) {
            if (item.getStockAvailable() != null && item.getQuantity() > item.getStockAvailable()) {
                PosProductCacheEntity product = productCacheRepository.findByProductId(item.getProductId())
                        .orElse(null);
                String productName = product != null ? product.getTitle() : "Product #" + item.getProductId();
                outOfStockItems.add(productName);
            }
        }
        if (!outOfStockItems.isEmpty()) {
            throw new BadRequestException("Items out of stock: " + String.join(", ", outOfStockItems));
        }
    }

    private List<Map<String, Object>> buildPosOrderItemsList(List<PosCartItemEntity> cartItems) {
        return cartItems.stream()
                .map(item -> {
                    PosProductCacheEntity product = productCacheRepository.findByProductId(item.getProductId())
                            .orElse(null);
                    Map<String, Object> itemMap = new HashMap<>();
                    itemMap.put("productId", item.getProductId());
                    itemMap.put("variantId", item.getVariantId());
                    itemMap.put("productName", product != null ? product.getTitle() : "Product #" + item.getProductId());
                    itemMap.put("variantName", null);
                    itemMap.put("quantity", item.getQuantity());
                    itemMap.put("unitPrice", item.getUnitPrice());
                    itemMap.put("totalPrice", item.getTotalPrice());
                    itemMap.put("itemWeight", BigDecimal.ZERO);
                    itemMap.put("itemTotalWeight", BigDecimal.ZERO);
                    return itemMap;
                })
                .collect(Collectors.toList());
    }

    private ShipmentCreationResponse createPosShipment(Long orderId, BigDecimal shippingCost,
                                                        ShippingCostResponse shippingCostResponse,
                                                        Long paymentMethodId) {
        try {
            Long courierId = shippingCostResponse != null ? shippingCostResponse.getCourierId() : null;
            if (courierId == null) {
                log.warn("No courier ID available from shipping response - skipping shipment creation");
                return null;
            }

            ShipmentCreationRequest shipmentRequest = ShipmentCreationRequest.builder()
                    .requestId(UUID.randomUUID().toString())
                    .orderId(orderId)
                    .courierId(courierId)
                    .shipmentWeight(BigDecimal.ZERO)
                    .shippingCost(shippingCost)
                    .shippingBreakdown(shippingCostResponse.getBreakdown())
                    .paymentMethodId(paymentMethodId)
                    .payerType("CUSTOMER")
                    .build();
            return crossModuleLookupService.createShipment(shipmentRequest);
        } catch (Exception e) {
            log.error("Failed to create shipment for POS order", e);
            return null;
        }
    }

    /**
     * Decreases inventory stock for every sold cart item via the inventory module.
     */
    private void decreasePosInventory(List<PosCartItemEntity> items) {
        List<InventoryUpdateRequest.InventoryItem> inventoryItems = items.stream()
                .map(item -> InventoryUpdateRequest.InventoryItem.builder()
                        .productId(item.getProductId())
                        .variantId(item.getVariantId())
                        .quantity(item.getQuantity())
                        .build())
                .collect(Collectors.toList());

        InventoryUpdateResponse response = crossModuleLookupService.decreaseInventoryStock(inventoryItems);
        if (response == null || !Boolean.TRUE.equals(response.getSuccess())) {
            String reason = buildInventoryFailureReason(response);
            log.error("Inventory decrease failed for POS order: {}", reason);
            throw new BadRequestException("Failed to update inventory: " + reason);
        }
    }

    /**
     * Decrements the POS product cache stock snapshot for each sold item so pos_product_cache
     * stays consistent with the authoritative inventory decrease. Best-effort: the cache is a
     * non-authoritative snapshot, so a failure here must never roll back a completed sale.
     * Time complexity: O(n) for n cart items.
     */
    private void syncPosCacheStockAfterSale(List<PosCartItemEntity> items) {
        for (PosCartItemEntity item : items) {
            try {
                productCacheRepository.findByProductId(item.getProductId()).ifPresent(cache -> {
                    Integer current = cache.getStockAvailable();
                    if (current != null) {
                        cache.setStockAvailable(Math.max(0, current - item.getQuantity()));
                        cache.setDateUpdated(LocalDateTime.now(SRI_LANKA_ZONE));
                        productCacheRepository.save(cache);
                    }
                });
            } catch (Exception e) {
                log.warn("Failed to sync POS cache stock for product {}: {}", item.getProductId(), e.getMessage());
            }
        }
    }

    /**
     * Builds a human-readable reason from a failed inventory update response.
     */
    private String buildInventoryFailureReason(InventoryUpdateResponse response) {
        if (response == null) {
            return "No response from inventory module";
        }
        if (response.getFailedItems() != null && !response.getFailedItems().isEmpty()) {
            return response.getFailedItems().stream()
                    .map(failed -> "product " + failed.getProductId()
                            + (failed.getVariantId() != null ? " (variant " + failed.getVariantId() + ")" : "")
                            + ": " + failed.getReason())
                    .collect(Collectors.joining("; "));
        }
        return response.getErrorMessage() != null ? response.getErrorMessage() : "Unknown inventory error";
    }

    private PaymentRequestCreationResponse createPosPaymentRequest(String orderUuid, Long orderId,
                                                                     String orderNumber,
                                                                     PaymentMethodLookupResponse paymentMethod,
                                                                     BigDecimal amount) {
        try {
            if (paymentMethod == null || paymentMethod.getMethodId() == null) {
                log.info("Skipping payment request creation - no payment method provided for order {}", orderNumber);
                return null;
            }

            PaymentRequestCreationRequest request = PaymentRequestCreationRequest.builder()
                    .requestId(UUID.randomUUID().toString())
                    .orderUuid(orderUuid)
                    .orderId(orderId)
                    .orderNumber(orderNumber)
                    .methodId(paymentMethod.getMethodId())
                    .amount(amount)
                    .currency("LKR")
                    .returnUrl("http://localhost:3000/order/success")
                    .cancelUrl("http://localhost:3000/order/cancel")
                    .build();
            return crossModuleLookupService.createPaymentRequest(request);
        } catch (Exception e) {
            log.error("Failed to create payment request for POS order", e);
            return null;
        }
    }

    private Long normalizeOptionalId(Long value) {
        return value != null && value > 0 ? value : null;
    }

    private Long toLong(Object value) {
        switch (value) {
            case null -> {
                return null;
            }
            case Long l -> {
                return l;
            }
            case Integer i -> {
                return i.longValue();
            }
            case String s -> {
                try {
                    return Long.parseLong(s);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            default -> {
            }
        }
        return null;
    }
}

