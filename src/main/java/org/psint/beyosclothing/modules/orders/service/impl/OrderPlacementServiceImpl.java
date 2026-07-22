package org.psint.beyosclothing.modules.orders.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.core.exception.BadRequestException;
import org.psint.beyosclothing.core.exception.ResourceNotFoundException;
import org.psint.beyosclothing.modules.orders.dto.external.*;
import org.psint.beyosclothing.modules.orders.dto.request.PlaceOrderRequest;
import org.psint.beyosclothing.modules.orders.dto.response.OrderDetailResponse;
import org.psint.beyosclothing.modules.orders.dto.response.OrderPlacementResponse;
import org.psint.beyosclothing.modules.orders.entity.*;
import org.psint.beyosclothing.modules.orders.repository.*;
import org.psint.beyosclothing.modules.orders.service.OrderCrossModuleLookupService;
import org.psint.beyosclothing.modules.orders.service.OrderPlacementService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Order Placement Service Implementation
 * Handles complete order placement flow with multi-module coordination
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderPlacementServiceImpl implements OrderPlacementService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderShippingAddressRepository shippingAddressRepository;
    private final OrderStatusHistoryRepository statusHistoryRepository;
    private final OrderCrossModuleLookupService crossModuleLookupService;

    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;

    private static final AtomicInteger orderSequence = new AtomicInteger(0);

    @Override
    @Transactional
    public OrderPlacementResponse placeOrder(PlaceOrderRequest request, String customerUuid, String guestSessionToken) {
        log.info("Processing order placement - Customer UUID: {}, Guest Token: {}",
                customerUuid, guestSessionToken != null ? "***" : null);

        try {
            // ===== STEP 1: Fetch Cart Items =====
            CartItemsForOrderResponse cartResponse = crossModuleLookupService.getCartItemsForOrder(
                    customerUuid, guestSessionToken, request.getCartItemUuids());

            if (cartResponse == null || !cartResponse.getFound() || cartResponse.getItems().isEmpty()) {
                throw new BadRequestException("Cart is empty or items not found");
            }

            log.info("Cart items retrieved - {} items, Subtotal: {}",
                    cartResponse.getItems().size(), cartResponse.getSubtotal());

            // ===== STEP 2: Validate Stock Availability =====
            validateStockAvailability(cartResponse.getItems());

            // ===== STEP 3: Fetch Customer Details =====
            CustomerLookupResponse customer = null;
            if (customerUuid != null) {
                customer = crossModuleLookupService.lookupCustomer(customerUuid);
                if (customer == null || !customer.getFound()) {
                    throw new ResourceNotFoundException("Customer", "uuid", customerUuid);
                }
            }

            // ===== STEP 4: Fetch and Validate Delivery Address =====
            CustomerAddressResponse address = crossModuleLookupService.lookupCustomerAddress(
                    customer != null ? customer.getCustomerId() : null,
                    request.getDeliveryAddressId());

            if (address == null || !address.getFound()) {
                throw new ResourceNotFoundException("Address", "id", request.getDeliveryAddressId().toString());
            }

            // ===== STEP 5: Fetch Payment Method Details =====
            PaymentMethodLookupResponse paymentMethod = crossModuleLookupService.lookupPaymentMethod(
                    request.getPaymentMethodId());

            if (paymentMethod == null || !paymentMethod.getFound() || !paymentMethod.getIsActive()) {
                throw new ResourceNotFoundException("Payment Method", "id", request.getPaymentMethodId().toString());
            }

            // ===== STEP 6: Calculate Shipping Cost =====
            String customerType = customer != null ? customer.getCustomerType() : "CUSTOMER";
            ShippingCostResponse shippingCost = crossModuleLookupService.calculateShippingCost(
                    String.valueOf(request.getCourierId()),
                    cartResponse.getTotalWeight(),
                    request.getPaymentMethodId(),
                    customerType
            );

            if (shippingCost == null || !shippingCost.getFound()) {
                throw new ResourceNotFoundException("Courier", "id", request.getCourierId().toString());
            }

            log.info("Shipping cost calculated - Courier: {}, Cost: {}, Weight: {} kg",
                    shippingCost.getCourierName(), shippingCost.getShippingCost(), shippingCost.getTotalWeight());

            // ===== STEP 7: Calculate Totals =====
            BigDecimal subtotal = cartResponse.getSubtotal();
            BigDecimal promoDiscount = request.getUsePromoCode() && cartResponse.getPromoDiscount() != null
                    ? cartResponse.getPromoDiscount()
                    : BigDecimal.ZERO;
            BigDecimal shippingFee = shippingCost.getIsFree() ? BigDecimal.ZERO : shippingCost.getShippingCost();
            BigDecimal total = subtotal.subtract(promoDiscount).add(shippingFee);

            // ===== STEP 8: Generate Order Number =====
            String orderNumber = generateOrderNumber();
            String orderUuid = UUID.randomUUID().toString();

            log.info("Order calculations - Subtotal: {}, Promo Discount: {}, Shipping: {}, Total: {}",
                    subtotal, promoDiscount, shippingFee, total);

            // ===== STEP 9: Create Order Entity =====
            OrderEntity order = OrderEntity.builder()
                    .uuid(orderUuid)
                    .customerId(customer != null ? customer.getCustomerId() : null)
                    .resellerId(null) // TODO: Handle reseller logic
                    .cartId(cartResponse.getCartId())
                    .orderNumber(orderNumber)
                    .status(OrderEntity.OrderStatus.PENDING)
                    .paymentStatus(OrderEntity.PaymentStatus.UNPAID)
                    .paymentMethod(paymentMethod.getMethodName())
                    .paymentMethodId(paymentMethod.getMethodId())
                    .subtotal(subtotal)
                    .discountTotal(promoDiscount)
                    .shippingCost(shippingFee)
                    .shippingWeight(cartResponse.getTotalWeight())
                    .shippingBreakdown(shippingCost.getBreakdown())
                    .shippingPayer(OrderEntity.ShippingPayer.CUSTOMER)
                    .total(total)
                    .promoCode(request.getUsePromoCode() ? cartResponse.getPromoCode() : null)
                    .promoDiscount(promoDiscount)
                    .notes(request.getCustomerNotes())
                    .build();

            order = orderRepository.save(order);
            log.info("Order created - Order Number: {}, UUID: {}", orderNumber, orderUuid);

            // ===== STEP 10: Create Order Items =====
            List<OrderItemEntity> orderItems = createOrderItems(order.getId(), cartResponse.getItems());
            orderItemRepository.saveAll(orderItems);
            log.info("Order items created - {} items", orderItems.size());

            // ===== STEP 11: Create Shipping Address =====
            OrderShippingAddressEntity shippingAddress = createShippingAddress(order.getId(), address);
            shippingAddressRepository.save(shippingAddress);

            // ===== STEP 12: Create Order Status History =====
            createStatusHistory(order.getId(), null, OrderEntity.OrderStatus.PENDING.name(), "Order placed");

            // ===== STEP 13: Create Shipment in Delivery Module =====
            ShipmentCreationResponse shipment = createShipment(order, request, shippingCost);

            if (shipment != null && shipment.getSuccess()) {
                order.setShipmentId(shipment.getShipmentId());
                orderRepository.save(order);
                log.info("Shipment created - Shipment ID: {}", shipment.getShipmentId());
            } else {
                log.warn("Shipment creation failed - Order will proceed without shipment link");
            }

            // ===== STEP 14: Decrease Inventory Stock =====
            decreaseInventory(cartResponse.getItems());

            // ===== STEP 15: Create Payment Request =====
            PaymentRequestCreationResponse paymentRequest = createPaymentRequest(
                    order, paymentMethod, total, address);

            String redirectUrl = null;
            if (paymentRequest != null && paymentRequest.getSuccess()) {
                redirectUrl = paymentRequest.getRedirectUrl();
                log.info("Payment request created - Status: {}, Redirect URL: {}",
                        paymentRequest.getStatus(), redirectUrl != null ? "Yes" : "No");
            }

            // ===== STEP 16: Clear Cart Items =====
            clearCart(cartResponse.getCartId(),
                    cartResponse.getItems().stream()
                            .map(CartItemsForOrderResponse.CartItemInfo::getItemUuid)
                            .collect(Collectors.toList()));

            // ===== STEP 17: Build Response =====
            OrderPlacementResponse response = OrderPlacementResponse.builder()
                    .orderUuid(orderUuid)
                    .orderNumber(orderNumber)
                    .status(order.getStatus().name())
                    .paymentStatus(order.getPaymentStatus().name())
                    .subtotal(subtotal)
                    .discountTotal(promoDiscount)
                    .shippingCost(shippingFee)
                    .total(total)
                    .paymentRedirectUrl(redirectUrl)
                    .paymentMethodCode(paymentMethod.getMethodCode())
                    .paymentMethodName(paymentMethod.getMethodName())
                    .estimatedDeliveryDate(LocalDateTime.now().plusDays(7)) // TODO: Calculate based on courier
                    .createdAt(order.getCreatedAt())
                    .message("Order placed successfully")
                    .build();

            log.info("Order placement completed successfully - Order Number: {}", orderNumber);
            return response;

        } catch (BadRequestException | ResourceNotFoundException e) {
            log.error("Order placement validation failed: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Order placement failed", e);
            throw new RuntimeException("Failed to place order: " + e.getMessage(), e);
        }
    }

    @Transactional
    public OrderEntity persistPosOrder(
            OrderEntity order,
            List<OrderItemEntity> orderItems,
            OrderShippingAddressEntity shippingAddress,
            String statusNotes
    ) {
        if (order == null) {
            throw new BadRequestException("Order is required");
        }

        OrderEntity savedOrder = orderRepository.save(order);

        if (orderItems != null && !orderItems.isEmpty()) {
            orderItems.forEach(item -> item.setOrderId(savedOrder.getId()));
            orderItemRepository.saveAll(orderItems);
        }

        if (shippingAddress != null) {
            shippingAddress.setOrderId(savedOrder.getId());
            shippingAddressRepository.save(shippingAddress);
        }

        createStatusHistory(
                savedOrder.getId(),
                null,
                savedOrder.getStatus() != null ? savedOrder.getStatus().name() : OrderEntity.OrderStatus.PENDING.name(),
                statusNotes != null ? statusNotes : "POS order placed"
        );

        return savedOrder;
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDetailResponse getOrderDetails(String orderUuid, String customerUuid) {
        log.debug("Fetching order details - UUID: {}", orderUuid);

        OrderEntity order = orderRepository.findByUuid(orderUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "uuid", orderUuid));

        // TODO: Authorization check - verify customer owns this order

        List<OrderItemEntity> items = orderItemRepository.findByOrderId(order.getId());
        OrderShippingAddressEntity shippingAddress = shippingAddressRepository.findByOrderId(order.getId())
                .orElse(null);

        return buildOrderDetailResponse(order, items, shippingAddress);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderDetailResponse> getCustomerOrders(String customerUuid, Pageable pageable) {
        log.debug("Fetching orders for customer UUID: {}", customerUuid);

        // Lookup customer ID from UUID
        CustomerLookupResponse customer = crossModuleLookupService.lookupCustomer(customerUuid);
        if (customer == null || !customer.getFound()) {
            throw new ResourceNotFoundException("Customer", "uuid", customerUuid);
        }

        Page<OrderEntity> orders = orderRepository.findByCustomerIdOrderByCreatedAtDesc(
                customer.getCustomerId(), pageable);

        return orders.map(order -> {
            List<OrderItemEntity> items = orderItemRepository.findByOrderId(order.getId());
            OrderShippingAddressEntity address = shippingAddressRepository.findByOrderId(order.getId())
                    .orElse(null);
            return buildOrderDetailResponse(order, items, address);
        });
    }

    // ===== PRIVATE HELPER METHODS =====

    private void validateStockAvailability(List<CartItemsForOrderResponse.CartItemInfo> items) {
        List<String> outOfStockItems = new ArrayList<>();

        for (CartItemsForOrderResponse.CartItemInfo item : items) {
            if (item.getAvailableStock() != null && item.getQuantity() > item.getAvailableStock()) {
                outOfStockItems.add(item.getProductTitle() +
                        (item.getVariantTitle() != null ? " - " + item.getVariantTitle() : ""));
            }
        }

        if (!outOfStockItems.isEmpty()) {
            throw new BadRequestException("Items out of stock: " + String.join(", ", outOfStockItems));
        }
    }

    private String generateOrderNumber() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int sequence = orderSequence.incrementAndGet() % 10000;
        String orderNumber = String.format("BYS-%s-%04d", date, sequence);

        // Ensure uniqueness
        while (orderRepository.existsByOrderNumber(orderNumber)) {
            sequence = orderSequence.incrementAndGet() % 10000;
            orderNumber = String.format("BYS-%s-%04d", date, sequence);
        }

        return orderNumber;
    }

    private List<OrderItemEntity> createOrderItems(Long orderId, List<CartItemsForOrderResponse.CartItemInfo> cartItems) {
        return cartItems.stream()
                .map(item -> OrderItemEntity.builder()
                        .uuid(UUID.randomUUID().toString())
                        .orderId(orderId)
                        .productId(item.getProductId())
                        .variantId(item.getVariantId())
                        .productTitle(item.getProductTitle())
                        .variantTitle(item.getVariantTitle())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .totalPrice(item.getTotalPrice())
                        .itemWeight(item.getItemWeight())
                        .itemTotalWeight(item.getTotalItemWeight())
                        .isRefunded(false)
                        .build())
                .collect(Collectors.toList());
    }

    private OrderShippingAddressEntity createShippingAddress(Long orderId, CustomerAddressResponse address) {
        return OrderShippingAddressEntity.builder()
                .orderId(orderId)
                .fullName(address.getFullName())
                .phone(address.getPhoneNumber())
                .email(address.getEmail())
                .addressLine1(address.getAddressLine1())
                .addressLine2(address.getAddressLine2())
                .city(address.getCity())
                .province(address.getProvince())
                .postalCode(address.getPostalCode())
                .country(address.getCountry())
                .build();
    }

    private void createStatusHistory(Long orderId, String oldStatus, String newStatus, String notes) {
        OrderStatusHistoryEntity history = OrderStatusHistoryEntity.builder()
                .orderId(orderId)
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .notes(notes)
                .build();
        statusHistoryRepository.save(history);
    }

    private ShipmentCreationResponse createShipment(OrderEntity order, PlaceOrderRequest request,
                                                     ShippingCostResponse shippingCost) {
        try {
            ShipmentCreationRequest shipmentRequest = ShipmentCreationRequest.builder()
                    .requestId(UUID.randomUUID().toString())
                    .orderId(order.getId())
                    .courierId(shippingCost.getCourierId())
                    .shipmentWeight(order.getShippingWeight())
                    .shippingCost(order.getShippingCost())
                    .shippingBreakdown(shippingCost.getBreakdown())
                    .paymentMethodId(request.getPaymentMethodId())
                    .payerType(order.getShippingPayer().name())
                    .build();

            return crossModuleLookupService.createShipment(shipmentRequest);
        } catch (Exception e) {
            log.error("Failed to create shipment", e);
            return null;
        }
    }

    private void decreaseInventory(List<CartItemsForOrderResponse.CartItemInfo> items) {
        try {
            List<InventoryUpdateRequest.InventoryItem> inventoryItems = items.stream()
                    .map(item -> InventoryUpdateRequest.InventoryItem.builder()
                            .productId(item.getProductId())
                            .variantId(item.getVariantId())
                            .quantity(item.getQuantity())
                            .build())
                    .collect(Collectors.toList());

            InventoryUpdateResponse response = crossModuleLookupService.decreaseInventoryStock(inventoryItems);

            if (response == null || !response.getSuccess()) {
                log.warn("Inventory update failed: {}", response != null ? response.getErrorMessage() : "No response");
            }
        } catch (Exception e) {
            log.error("Failed to decrease inventory", e);
        }
    }

    private PaymentRequestCreationResponse createPaymentRequest(OrderEntity order,
                                                                  PaymentMethodLookupResponse paymentMethod,
                                                                  BigDecimal amount,
                                                                  CustomerAddressResponse address) {
        try {
            String firstName = null;
            String lastName = null;
            if (address != null && address.getFullName() != null && !address.getFullName().isBlank()) {
                String[] parts = address.getFullName().trim().split("\\s+", 2);
                firstName = parts[0];
                lastName = parts.length > 1 ? parts[1] : parts[0];
            }

            PaymentRequestCreationRequest request = PaymentRequestCreationRequest.builder()
                    .requestId(UUID.randomUUID().toString())
                    .orderUuid(order.getUuid())
                    .orderId(order.getId())
                    .orderNumber(order.getOrderNumber()) // ✅ Pass actual order number
                    .methodId(paymentMethod.getMethodId())
                    .amount(amount)
                    .currency("LKR")
                    .returnUrl(frontendBaseUrl + "/order/success")
                    .cancelUrl(frontendBaseUrl + "/order/cancel")
                    .customerFirstName(firstName)
                    .customerLastName(lastName)
                    .customerEmail(address != null ? address.getEmail() : null)
                    .customerPhone(address != null ? address.getPhoneNumber() : null)
                    .build();

            return crossModuleLookupService.createPaymentRequest(request);
        } catch (Exception e) {
            log.error("Failed to create payment request", e);
            return null;
        }
    }

    private void clearCart(Long cartId, List<String> itemUuids) {
        try {
            CartClearResponse response = crossModuleLookupService.clearCartItems(cartId, itemUuids);

            if (response != null && response.getSuccess()) {
                log.info("Cart cleared - {} items removed", response.getItemsCleared());
            } else {
                log.warn("Cart clear failed: {}", response != null ? response.getErrorMessage() : "No response");
            }
        } catch (Exception e) {
            log.error("Failed to clear cart", e);
        }
    }

    private OrderDetailResponse buildOrderDetailResponse(OrderEntity order,
                                                          List<OrderItemEntity> items,
                                                          OrderShippingAddressEntity address) {
        List<OrderDetailResponse.OrderItemInfo> itemInfos = items.stream()
                .map(item -> OrderDetailResponse.OrderItemInfo.builder()
                        .uuid(item.getUuid())
                        .productTitle(item.getProductTitle())
                        .variantTitle(item.getVariantTitle())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .totalPrice(item.getTotalPrice())
                        .itemWeight(item.getItemWeight())
                        .isRefunded(item.getIsRefunded())
                        .build())
                .collect(Collectors.toList());

        OrderDetailResponse.ShippingAddressInfo addressInfo = null;
        if (address != null) {
            addressInfo = OrderDetailResponse.ShippingAddressInfo.builder()
                    .fullName(address.getFullName())
                    .phoneNumber(address.getPhone())
                    .addressLine1(address.getAddressLine1())
                    .addressLine2(address.getAddressLine2())
                    .city(address.getCity())
                    .state(address.getProvince())
                    .postalCode(address.getPostalCode())
                    .country(address.getCountry())
                    .build();
        }

        // TODO: Fetch tracking info from shipment

        return OrderDetailResponse.builder()
                .orderUuid(order.getUuid())
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus().name())
                .paymentStatus(order.getPaymentStatus().name())
                .paymentMethod(order.getPaymentMethod())
                .subtotal(order.getSubtotal())
                .discountTotal(order.getDiscountTotal())
                .shippingCost(order.getShippingCost())
                .total(order.getTotal())
                .promoCode(order.getPromoCode())
                .promoDiscount(order.getPromoDiscount())
                .notes(order.getNotes())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .items(itemInfos)
                .shippingAddress(addressInfo)
                .tracking(null) // TODO: Implement tracking lookup
                .build();
    }
}
