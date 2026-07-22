package org.psint.beyosclothing.modules.resellers.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.common.dto.PageResponse;
import org.psint.beyosclothing.modules.inventory.dto.request.InventoryUpdateRequest;
import org.psint.beyosclothing.modules.inventory.dto.response.InventoryUpdateResponse;
import org.psint.beyosclothing.modules.resellers.dto.request.ResellerOrderPlacementRequest;
import org.psint.beyosclothing.modules.resellers.dto.response.ResellerOrderDetailResponse;
import org.psint.beyosclothing.modules.resellers.dto.response.ResellerOrderListResponse;
import org.psint.beyosclothing.modules.resellers.dto.response.ResellerOrderResponse;
import org.psint.beyosclothing.modules.resellers.entity.Reseller;
import org.psint.beyosclothing.modules.resellers.entity.ResellerCart;
import org.psint.beyosclothing.modules.resellers.entity.ResellerCartItem;
import org.psint.beyosclothing.modules.resellers.entity.ResellerPriceOverride;
import org.psint.beyosclothing.modules.resellers.repository.ResellerCartItemRepository;
import org.psint.beyosclothing.modules.resellers.repository.ResellerCartRepository;
import org.psint.beyosclothing.modules.resellers.repository.ResellerPriceOverrideRepository;
import org.psint.beyosclothing.modules.resellers.service.ResellerOrderService;
import org.psint.beyosclothing.modules.resellers.service.ResellerSecurityService;
import org.psint.beyosclothing.modules.resellers.service.ResellerService;
import org.psint.beyosclothing.modules.sms.service.OrderSmsNotificationService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Implementation of Reseller Order Service
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional("resellerTransactionManager")
public class ResellerOrderServiceImpl implements ResellerOrderService {

    private final ResellerService resellerService;
    private final ResellerSecurityService securityService;
    private final ResellerCartRepository cartRepository;
    private final ResellerCartItemRepository cartItemRepository;
    private final ResellerPriceOverrideRepository priceOverrideRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final OrderSmsNotificationService smsNotificationService;


    @Value("${app.rabbitmq.exchange.inventory:beyos.exchange.inventory}")
    private String inventoryExchange;

    @Value("${app.rabbitmq.exchange.delivery:beyos.exchange.delivery}")
    private String deliveryExchange;

    @Value("${app.rabbitmq.exchange.order:beyos.exchange.order}")
    private String orderExchange;

    @Value("${app.rabbitmq.exchange.reseller:beyos.exchange.reseller}")
    private String resellerExchange;

    @Override
    public ResellerOrderResponse placeOrder(Long userId, ResellerOrderPlacementRequest request) {
        // Validate reseller is APPROVED
        securityService.checkApprovedStatus(userId);
        Reseller reseller = resellerService.getResellerByUserId(userId);

        // Fetch active cart
        ResellerCart cart = cartRepository.findByResellerIdAndIsActive(reseller.getId(), true)
                .orElseThrow(() -> new IllegalArgumentException("No active cart found"));

        List<ResellerCartItem> cartItems = cartItemRepository.findByCartIdAndIsActive(cart.getId(),true);

        if (cartItems.isEmpty()) {
            throw new IllegalArgumentException("Cart is empty");
        }

        // Validate stock for all items
        validateAllItemsStock(cartItems);

        // Calculate totals
        BigDecimal subtotal = calculateSubtotal(cartItems);
        BigDecimal deliveryCharge = fetchDeliveryCharges(request.getCourierUuid(), cartItems);
        BigDecimal total = subtotal.add(deliveryCharge);

        // Decrease inventory
        decreaseStock(cartItems);

        // Create order via RabbitMQ
        OrderCreationResult creationResult = createOrderViaRabbitMQ(reseller, request, cartItems, subtotal, deliveryCharge, total, cart.getId());
        Long orderId = creationResult.orderId();

        // Create price override entries
        createPriceOverrides(reseller.getId(), orderId, cartItems);

        // Clear cart
        clearCart(cart);

        // Publish event
        publishResellerOrderPlacedEvent(reseller, orderId);

        // Send confirmation email
        sendOrderConfirmationEmail(reseller, orderId);

        // Send order placed SMS to the reseller (looked up via their reseller record's phone)
        smsNotificationService.sendOrderConfirmation(
                reseller.getPhone(), creationResult.orderNumber(), total, creationResult.status());

        log.info("Order placed successfully for reseller: {} order ID: {}", reseller.getUuid(), orderId);

        return buildOrderResponse(orderId, cartItems, subtotal, deliveryCharge, total);
    }

    @Override
    public ResellerOrderListResponse getOrders(Long userId, Pageable pageable) {
        Reseller reseller = resellerService.getResellerByUserId(userId);

        // Fetch orders via RabbitMQ
        log.info("Fetching orders for reseller: {}", reseller.getUuid());

        // TODO: Implement RabbitMQ call to order module
        return ResellerOrderListResponse.builder()
                .orders(List.of())
                .currentPage(pageable.getPageNumber())
                .totalPages(0)
                .totalOrders(0L)
                .pageSize(pageable.getPageSize())
                .build();
    }

    @Override
    public ResellerOrderDetailResponse getOrderByUuid(Long userId, String orderUuid) {
        Reseller reseller = resellerService.getResellerByUserId(userId);

        log.info("Fetching order details: {} for reseller: {}", orderUuid, reseller.getUuid());

        try {
            // Validate UUID format
            validateUuidFormat(orderUuid);

            // Build RabbitMQ request
            Map<String, Object> request = new HashMap<>();
            request.put("orderUuid", orderUuid);
            request.put("resellerId", reseller.getId());

            rabbitTemplate.setReplyTimeout(TimeUnit.SECONDS.toMillis(5));

            // Send request to order module and wait for response
            Object response = rabbitTemplate.convertSendAndReceive(
                    orderExchange,
                    "order.detail.lookup.request",
                    request
            );

            if (response instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> responseMap = (Map<String, Object>) response;

                Boolean found = (Boolean) responseMap.get("found");

                if (Boolean.TRUE.equals(found)) {
                    Boolean belongsToReseller = (Boolean) responseMap.get("belongsToReseller");

                    if (Boolean.FALSE.equals(belongsToReseller)) {
                        log.warn("Reseller {} attempted to access order {} that doesn't belong to them",
                                userId, orderUuid);
                        throw new SecurityException("Order does not belong to this reseller");
                    }

                    log.info("Order details retrieved successfully for UUID: {}", orderUuid);
                    return mapToOrderDetailResponse(responseMap);
                }
            }

            log.warn("Order not found or invalid response for UUID: {}", orderUuid);
            throw new org.psint.beyosclothing.core.exception.ResourceNotFoundException("Order", "uuid", orderUuid);

        } catch (SecurityException e) {
            log.warn("Security exception while fetching order {}: {}", orderUuid, e.getMessage());
            throw e;
        } catch (org.psint.beyosclothing.core.exception.ResourceNotFoundException e) {
            log.warn("Order not found: {}", orderUuid);
            throw e;
        } catch (Exception e) {
            log.error("Error fetching order details for UUID: {} by reseller: {}", orderUuid, userId, e);
            throw new RuntimeException("Failed to fetch order details: " + e.getMessage(), e);
        }
    }

    @Override
    public ResellerOrderListResponse getPendingOrders(Long userId, Pageable pageable) {
        Reseller reseller = resellerService.getResellerByUserId(userId);

        // Fetch pending orders via RabbitMQ
        log.info("Fetching pending orders for reseller: {}", reseller.getUuid());

        // TODO: Implement RabbitMQ call to order module with status filter
        return ResellerOrderListResponse.builder()
                .orders(List.of())
                .currentPage(pageable.getPageNumber())
                .totalPages(0)
                .totalOrders(0L)
                .pageSize(pageable.getPageSize())
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public PageResponse<ResellerOrderListResponse.OrderSummary> getPendingOrdersWithFilters(
            Long userId,
            String search,
            LocalDate startDate,
            LocalDate endDate,
            int page,
            int size) {

        Reseller reseller = resellerService.getResellerByUserId(userId);

        log.info("Fetching pending orders for reseller: {} - search: {}, startDate: {}, endDate: {}, page: {}, size: {}",
                reseller.getUuid(), search, startDate, endDate, page, size);

        try {
            // Build RabbitMQ request using Map
            Map<String, Object> request = new HashMap<>();
            request.put("resellerId", reseller.getId());
            request.put("search", search);
            request.put("statuses", List.of("PENDING", "PROCESSING", "OUT_FOR_DELIVERY"));
            request.put("startDate", startDate != null ? startDate.toString() : null);
            request.put("endDate", endDate != null ? endDate.toString() : null);
            request.put("page", page);
            request.put("size", size);

            rabbitTemplate.setReplyTimeout(TimeUnit.SECONDS.toMillis(5));

            Object response = rabbitTemplate.convertSendAndReceive(
                    orderExchange,
                    "reseller.pending.orders.list.lookup.request",
                    request
            );

            if (response instanceof Map) {
                Map<String, Object> responseMap = (Map<String, Object>) response;
                Boolean success = (Boolean) responseMap.get("success");

                if (Boolean.TRUE.equals(success)) {
                    List<Map<String, Object>> orderMaps = (List<Map<String, Object>>) responseMap.get("orders");
                    Long totalElements = getLongValue(responseMap.get("totalElements"));
                    Integer totalPages = getIntegerValue(responseMap.get("totalPages"));
                    Integer pageNumber = getIntegerValue(responseMap.get("pageNumber"));
                    Integer pageSize = getIntegerValue(responseMap.get("pageSize"));
                    Boolean isFirst = (Boolean) responseMap.get("first");
                    Boolean isLast = (Boolean) responseMap.get("last");
                    Boolean isEmpty = (Boolean) responseMap.get("empty");

                    List<ResellerOrderListResponse.OrderSummary> orderSummaries = orderMaps != null ?
                            orderMaps.stream().map(this::mapToOrderSummary).collect(Collectors.toList()) :
                            new ArrayList<>();

                    log.info("Pending orders retrieved successfully - total: {}, page: {}/{}", 
                            totalElements, pageNumber, totalPages);

                    return PageResponse.<ResellerOrderListResponse.OrderSummary>builder()
                            .resellerName(reseller.getFullName())
                            .content(orderSummaries)
                            .pageNumber(pageNumber != null ? pageNumber : page)
                            .pageSize(pageSize != null ? pageSize : size)
                            .totalElements(totalElements != null ? totalElements : 0L)
                            .totalPages(totalPages != null ? totalPages : 0)
                            .last(isLast != null ? isLast : false)
                            .first(isFirst != null ? isFirst : (page == 0))
                            .empty(isEmpty != null ? isEmpty : true)
                            .build();
                }
            }

            log.warn("Invalid response from order service for pending orders - reseller: {}", reseller.getUuid());
            return buildEmptyPageResponse(page, size);

        } catch (Exception e) {
            log.error("Error fetching pending orders for userId: {}", userId, e);
            return buildEmptyPageResponse(page, size);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public PageResponse<ResellerOrderListResponse.OrderSummary> getOrdersWithFilters(
            Long userId,
            String search,
            String status,
            LocalDate startDate,
            LocalDate endDate,
            int page,
            int size) {

        Reseller reseller = resellerService.getResellerByUserId(userId);

        log.info("Fetching orders for reseller: {} - search: {}, status: {}, startDate: {}, endDate: {}, page: {}, size: {}",
                reseller.getUuid(), search, status, startDate, endDate, page, size);

        try {
            // Build RabbitMQ request using Map
            Map<String, Object> request = new HashMap<>();
            request.put("resellerId", reseller.getId());
            request.put("search", search);
            request.put("status", status);
            request.put("startDate", startDate != null ? startDate.toString() : null);
            request.put("endDate", endDate != null ? endDate.toString() : null);
            request.put("page", page);
            request.put("size", size);

            rabbitTemplate.setReplyTimeout(TimeUnit.SECONDS.toMillis(5));

            Object response = rabbitTemplate.convertSendAndReceive(
                    orderExchange,
                    "reseller.orders.list.lookup.request",
                    request
            );

            if (response instanceof Map) {
                Map<String, Object> responseMap = (Map<String, Object>) response;
                Boolean success = (Boolean) responseMap.get("success");

                if (Boolean.TRUE.equals(success)) {
                    List<Map<String, Object>> orderMaps = (List<Map<String, Object>>) responseMap.get("orders");
                    Long totalElements = getLongValue(responseMap.get("totalElements"));
                    Integer totalPages = getIntegerValue(responseMap.get("totalPages"));
                    Integer pageNumber = getIntegerValue(responseMap.get("pageNumber"));
                    Integer pageSize = getIntegerValue(responseMap.get("pageSize"));
                    Boolean isFirst = (Boolean) responseMap.get("first");
                    Boolean isLast = (Boolean) responseMap.get("last");
                    Boolean isEmpty = (Boolean) responseMap.get("empty");

                    List<ResellerOrderListResponse.OrderSummary> orderSummaries = orderMaps != null ?
                            orderMaps.stream().map(this::mapToOrderSummary).collect(Collectors.toList()) :
                            new ArrayList<>();

                    return PageResponse.<ResellerOrderListResponse.OrderSummary>builder()
                            .resellerName(reseller.getFullName())
                            .content(orderSummaries)
                            .pageNumber(pageNumber != null ? pageNumber : page)
                            .pageSize(pageSize != null ? pageSize : size)
                            .totalElements(totalElements != null ? totalElements : 0L)
                            .totalPages(totalPages != null ? totalPages : 0)
                            .last(isLast != null ? isLast : false)
                            .first(isFirst != null ? isFirst : (page == 0))
                            .empty(isEmpty != null ? isEmpty : true)
                            .build();
                }
            }

            log.warn("Invalid response from order service for reseller: {}", reseller.getUuid());
            return buildEmptyPageResponse(page, size);

        } catch (Exception e) {
            log.error("Error fetching reseller orders for userId: {}", userId, e);
            return buildEmptyPageResponse(page, size);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public PageResponse<ResellerOrderListResponse.OrderSummary> getOrdersByResellerUuid(
            String resellerUuid,
            String search,
            String status,
            LocalDate startDate,
            LocalDate endDate,
            int page,
            int size) {

        Reseller reseller = resellerService.getResellerByUuid(resellerUuid);

        log.info("Fetching reseller orders by UUID: {} - search: {}, status: {}, startDate: {}, endDate: {}, page: {}, size: {}",
                resellerUuid, search, status, startDate, endDate, page, size);

        try {
            Map<String, Object> request = new HashMap<>();
            request.put("resellerId", reseller.getId());
            request.put("search", search);
            request.put("status", status);
            request.put("startDate", startDate != null ? startDate.toString() : null);
            request.put("endDate", endDate != null ? endDate.toString() : null);
            request.put("page", page);
            request.put("size", size);

            rabbitTemplate.setReplyTimeout(TimeUnit.SECONDS.toMillis(5));

            Object response = rabbitTemplate.convertSendAndReceive(
                    orderExchange,
                    "reseller.orders.list.lookup.request",
                    request
            );

            if (response instanceof Map) {
                Map<String, Object> responseMap = (Map<String, Object>) response;
                Boolean success = (Boolean) responseMap.get("success");

                if (Boolean.TRUE.equals(success)) {
                    List<Map<String, Object>> orderMaps = (List<Map<String, Object>>) responseMap.get("orders");
                    Long totalElements = getLongValue(responseMap.get("totalElements"));
                    Integer totalPages = getIntegerValue(responseMap.get("totalPages"));
                    Integer pageNumber = getIntegerValue(responseMap.get("pageNumber"));
                    Integer pageSize = getIntegerValue(responseMap.get("pageSize"));
                    Boolean isFirst = (Boolean) responseMap.get("first");
                    Boolean isLast = (Boolean) responseMap.get("last");
                    Boolean isEmpty = (Boolean) responseMap.get("empty");

                    List<ResellerOrderListResponse.OrderSummary> orderSummaries = orderMaps != null
                            ? orderMaps.stream().map(this::mapToOrderSummary).collect(Collectors.toList())
                            : new ArrayList<>();

                    return PageResponse.<ResellerOrderListResponse.OrderSummary>builder()
                            .resellerName(reseller.getFullName())
                            .content(orderSummaries)
                            .pageNumber(pageNumber != null ? pageNumber : page)
                            .pageSize(pageSize != null ? pageSize : size)
                            .totalElements(totalElements != null ? totalElements : 0L)
                            .totalPages(totalPages != null ? totalPages : 0)
                            .last(isLast != null ? isLast : false)
                            .first(isFirst != null ? isFirst : (page == 0))
                            .empty(isEmpty != null ? isEmpty : true)
                            .build();
                }
            }

            log.warn("Invalid response from order service for reseller UUID: {}", resellerUuid);
            return buildEmptyPageResponse(page, size);

        } catch (Exception e) {
            log.error("Error fetching reseller orders by UUID: {}", resellerUuid, e);
            return buildEmptyPageResponse(page, size);
        }
    }

    private ResellerOrderListResponse.OrderSummary mapToOrderSummary(Map<String, Object> orderMap) {
        return ResellerOrderListResponse.OrderSummary.builder()
                .orderUuid((String) orderMap.get("orderUuid"))
                .orderNumber((String) orderMap.get("orderNumber"))
                .orderDate(parseDateTime((String) orderMap.get("orderDate")))
                .customerName((String) orderMap.get("customerName"))
                .total(getBigDecimal(orderMap.get("amount")))
                .profit(getBigDecimal(orderMap.get("profit")))
                .status((String) orderMap.get("status"))
                .paymentStatus((String) orderMap.get("paymentStatus"))
                .reasonForCancellation((String) orderMap.get("reasonForCancellation"))
                .build();
    }

    private PageResponse<ResellerOrderListResponse.OrderSummary> buildEmptyPageResponse(int page, int size) {
        return PageResponse.<ResellerOrderListResponse.OrderSummary>builder()
                .content(new ArrayList<>())
                .pageNumber(page)
                .pageSize(size)
                .totalElements(0L)
                .totalPages(0)
                .last(true)
                .first(page == 0)
                .empty(true)
                .build();
    }

    @SuppressWarnings("unchecked")
    private ResellerOrderDetailResponse mapToOrderDetailResponse(Map<String, Object> responseMap) {
        ResellerOrderDetailResponse.ResellerOrderDetailResponseBuilder builder =
                ResellerOrderDetailResponse.builder();

        // Basic order info
        builder.orderUuid((String) responseMap.get("orderUuid"));
        builder.orderNumber((String) responseMap.get("orderNumber"));
        builder.orderDate(parseDateTime((String) responseMap.get("orderDate")));
        builder.orderStatus((String) responseMap.get("orderStatus"));
        builder.orderFrom((String) responseMap.get("orderFrom"));
        builder.orderType((String) responseMap.get("orderType"));
        builder.transactionId((String) responseMap.get("transactionId"));

        // Customer info
        Map<String, Object> customerMap = (Map<String, Object>) responseMap.get("customer");
        if (customerMap != null) {
            builder.customer(mapToCustomerInfo(customerMap));
        }

        // Order items
        List<Map<String, Object>> itemMaps = (List<Map<String, Object>>) responseMap.get("items");
        if (itemMaps != null) {
            builder.items(itemMaps.stream()
                    .map(this::mapToOrderItem)
                    .collect(Collectors.toList()));
        } else {
            builder.items(new ArrayList<>());
        }

        // Subtotal, shipping, tax, total
        builder.subtotal(getBigDecimal(responseMap.get("subtotal")));
        builder.shippingCost(getBigDecimal(responseMap.get("shippingCost")));
        builder.discountTotal(getBigDecimal(responseMap.get("discountTotal")));
        builder.tax(getBigDecimal(responseMap.get("tax")));
        builder.total(getBigDecimal(responseMap.get("total")));

        // Payment info
        Map<String, Object> paymentMap = (Map<String, Object>) responseMap.get("payment");
        if (paymentMap != null) {
            builder.payment(mapToPaymentInfo(paymentMap));
        }

        // Tracking info (nullable)
        Map<String, Object> trackingMap = (Map<String, Object>) responseMap.get("tracking");
        Long orderId = getLongValue(responseMap.get("orderId"));
        ResellerOrderDetailResponse.TrackingInfo trackingInfo = null;

        // Try to map from response tracking data first
        if (trackingMap != null && !trackingMap.isEmpty()) {
            trackingInfo = mapToTrackingInfo(trackingMap);
            log.debug("TrackingInfo mapped from response for orderId: {}", orderId);
        }

        // If no tracking from response or it's incomplete, fetch from delivery service
        if ((trackingInfo == null || isTrackingInfoIncomplete(trackingInfo)) && orderId > 0) {
            ResellerOrderDetailResponse.TrackingInfo fetchedTracking = fetchTrackingInfo(orderId);
            if (fetchedTracking != null) {
                trackingInfo = fetchedTracking;
                log.debug("TrackingInfo fetched from delivery service for orderId: {}", orderId);
            }
        }

        if (trackingInfo != null) {
            builder.tracking(trackingInfo);
        }

        // Order history
        List<Map<String, Object>> historyMaps = (List<Map<String, Object>>) responseMap.get("orderHistory");
        if (historyMaps != null) {
            builder.orderHistory(historyMaps.stream()
                    .map(this::mapToOrderHistoryEntry)
                    .collect(Collectors.toList()));
        } else {
            builder.orderHistory(new ArrayList<>());
        }

        return builder.build();
    }


    @SuppressWarnings("unchecked")
    private ResellerOrderDetailResponse.TrackingInfo fetchTrackingInfo(Long orderId) {
        if (orderId == null || orderId <= 0) {
            log.debug("Invalid orderId provided for tracking info fetch: {}", orderId);
            return null;
        }

        log.debug("Fetching tracking info via RabbitMQ for orderId: {}", orderId);

        try {
            Map<String, Object> request = new HashMap<>();
            request.put("orderId", orderId);

            rabbitTemplate.setReplyTimeout(TimeUnit.SECONDS.toMillis(5));
            Object response = rabbitTemplate.convertSendAndReceive(
                    deliveryExchange,
                    "shipment.lookup.request",
                    request
            );

            Map<String, Object> responseMap = null;
            if (response instanceof Map) {
                responseMap = (Map<String, Object>) response;
            } else if (response instanceof byte[]) {
                try {
                    responseMap = objectMapper.readValue((byte[]) response, new TypeReference<Map<String, Object>>() {});
                } catch (Exception e) {
                    log.warn("Failed to deserialize shipment lookup response for orderId={}: {}", orderId, e.getMessage());
                    return null;
                }
            }

            if (responseMap == null) {
                log.debug("Shipment lookup returned unsupported response type for orderId={}: {}",
                        orderId, response != null ? response.getClass().getName() : "null");
                return null;
            }

            Boolean found = getBoolean(responseMap.get("found"));
            if (!Boolean.TRUE.equals(found)) {
                log.debug("Shipment not found in delivery service for orderId: {}", orderId);
                return null;
            }

            String carrier = getStringValue(responseMap.get("carrier"));
            String courierCompany = getStringValue(responseMap.get("courierCompany"));
            String trackingNumber = getStringValue(responseMap.get("trackingNumber"));
            String trackingUrl = getStringValue(responseMap.get("trackingUrl"));
            String currentStatus = getStringValue(responseMap.get("currentStatus"));
            if (currentStatus == null) {
                currentStatus = getStringValue(responseMap.get("status"));
            }

            String shippedAt = getStringValue(responseMap.get("shippedAt"));
            String deliveredAt = getStringValue(responseMap.get("deliveredAt"));
            String expectedDelivery = buildExpectedDelivery(shippedAt, deliveredAt);

            // Keep response strict enough to avoid empty tracking objects.
            if (trackingNumber == null && carrier == null && courierCompany == null) {
                return null;
            }

            return ResellerOrderDetailResponse.TrackingInfo.builder()
                    .carrier(carrier != null ? carrier : courierCompany)
                    .courierCompany(courierCompany != null ? courierCompany : carrier)
                    .trackingNumber(trackingNumber)
                    .trackingUrl(trackingUrl)
                    .currentStatus(currentStatus)
                    .deliveredAt(parseDateTime(deliveredAt))
                    .expectedDelivery(LocalDate.parse(expectedDelivery))
                    .build();

        } catch (Exception e) {
            log.warn("Could not fetch tracking info for orderId={}: {}", orderId, e.getMessage());
        }

        return null;
    }

    private String buildExpectedDelivery(String shippedAt, String deliveredAt) {
        if (shippedAt != null && deliveredAt != null) {
            return shippedAt.substring(0, 10) + " - " + deliveredAt.substring(0, 10);
        }
        if (deliveredAt != null) {
            return deliveredAt.substring(0, 10);
        } else {
            assert shippedAt != null;
            return shippedAt.substring(0, 10);
        }
    }

    private boolean isTrackingInfoIncomplete(ResellerOrderDetailResponse.TrackingInfo trackingInfo) {
        if (trackingInfo == null) return true;

        // Tracking info is incomplete if it lacks both tracking number and carrier
        return trackingInfo.getTrackingNumber() == null && trackingInfo.getCarrier() == null;
    }


    private ResellerOrderDetailResponse.CustomerInfo mapToCustomerInfo(Map<String, Object> customerMap) {
        return ResellerOrderDetailResponse.CustomerInfo.builder()
                .fullName((String) customerMap.get("fullName"))
                .phone((String) customerMap.get("phone"))
                .email((String) customerMap.get("email"))
                .addressLine1((String) customerMap.get("addressLine1"))
                .addressLine2((String) customerMap.get("addressLine2"))
                .city((String) customerMap.get("city"))
                .district((String) customerMap.get("district"))
                .province((String) customerMap.get("province"))
                .postalCode((String) customerMap.get("postalCode"))
                .country((String) customerMap.get("country"))
                .build();
    }

    private ResellerOrderDetailResponse.OrderItemInfo mapToOrderItem(Map<String, Object> itemMap) {
        return ResellerOrderDetailResponse.OrderItemInfo.builder()
                .productName((String) itemMap.get("productName"))
                .variantName((String) itemMap.get("variantName"))
                .unitPrice(getBigDecimal(itemMap.get("unitPrice")))
                .basePrice(getBigDecimal(itemMap.get("basePrice")))
                .quantity(getInteger(itemMap.get("quantity")))
                .totalPrice(getBigDecimal(itemMap.get("totalPrice")))
                .margin(getBigDecimal(itemMap.get("margin")))
                .isRefunded(getBoolean(itemMap.get("isRefunded")))
                .build();
    }

    private ResellerOrderDetailResponse.PaymentInfo mapToPaymentInfo(Map<String, Object> paymentMap) {
        return ResellerOrderDetailResponse.PaymentInfo.builder()
                .paymentMethod((String) paymentMap.get("paymentMethod"))
                .paymentStatus((String) paymentMap.get("paymentStatus"))
                .transactionId((String) paymentMap.get("transactionId"))
                .paidAmount(getBigDecimal(paymentMap.get("paidAmount")))
                .paymentDate(parseDateTime((String) paymentMap.get("paymentDate")))
                .build();
    }

    private ResellerOrderDetailResponse.TrackingInfo mapToTrackingInfo(Map<String, Object> trackingMap) {
        if (trackingMap == null || trackingMap.isEmpty()) {
            return null;
        }

        LocalDateTime shippedAt = parseDateTime(getStringValue(trackingMap.get("shippedAt")));
        LocalDateTime deliveredAt = parseDateTime(getStringValue(trackingMap.get("deliveredAt")));
        String expectedDelivery = getStringValue(trackingMap.get("expectedDelivery"));
        String courierCompany = getStringValue(trackingMap.get("courierCompany"));
        String carrier = getStringValue(trackingMap.get("carrier"));
        String trackingNumber = getStringValue(trackingMap.get("trackingNumber"));
        String currentStatus = getStringValue(trackingMap.get("currentStatus"));
        if (currentStatus == null) {
            currentStatus = getStringValue(trackingMap.get("status"));
        }

        // Only build if we have essential tracking data.
        if (trackingNumber != null || courierCompany != null || carrier != null) {
            return ResellerOrderDetailResponse.TrackingInfo.builder()
                    .shippedAt(shippedAt)
                    .deliveredAt(deliveredAt)
                    .expectedDelivery(LocalDate.parse(expectedDelivery))
                    .courierCompany(courierCompany != null ? courierCompany : carrier)
                    .carrier(carrier != null ? carrier : courierCompany)
                    .trackingNumber(trackingNumber)
                    .currentStatus(currentStatus)
                    .trackingUrl(getStringValue(trackingMap.get("trackingUrl")))
                    .build();
        }

        return null;
    }

    private String getStringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }

    private ResellerOrderDetailResponse.OrderHistoryEntry mapToOrderHistoryEntry(Map<String, Object> historyMap) {
        return ResellerOrderDetailResponse.OrderHistoryEntry.builder()
                .status((String) historyMap.get("status"))
                .timestamp(parseDateTime((String) historyMap.get("timestamp")))
                .changedBy((String) historyMap.get("changedBy"))
                .notes((String) historyMap.get("notes"))
                .build();
    }

    private void validateUuidFormat(String orderUuid) {
        try {
            java.util.UUID.fromString(orderUuid);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid UUID format: " + orderUuid);
        }
    }

    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(dateTimeStr);
        } catch (Exception e) {
            log.warn("Failed to parse datetime: {}", dateTimeStr);
            return null;
        }
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr);
        } catch (Exception e) {
            log.warn("Failed to parse date: {}", dateStr);
            return null;
        }
    }

    private BigDecimal getBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return new BigDecimal(value.toString());
        if (value instanceof String) {
            try {
                return new BigDecimal((String) value);
            } catch (Exception e) {
                return BigDecimal.ZERO;
            }
        }
        return BigDecimal.ZERO;
    }

    private Integer getInteger(Object value) {
        if (value == null) return 0;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }

    private Long getLongValue(Object value) {
        if (value == null) return 0L;
        if (value instanceof Long) return (Long) value;
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (Exception e) {
                return 0L;
            }
        }
        return 0L;
    }

    private Integer getIntegerValue(Object value) {
        if (value == null) return 0;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }

    private Boolean getBoolean(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return false;
    }

    // Helper methods
    private void validateAllItemsStock(List<ResellerCartItem> items) {
        log.info("Validating stock for {} items via RabbitMQ", items.size());

        for (ResellerCartItem item : items) {
            try {
                Map<String, Object> request = new HashMap<>();
                request.put("productId", item.getProductId());
                request.put("variantId", item.getVariantId());
                request.put("requestedQuantity", item.getQuantity());

                rabbitTemplate.setReplyTimeout(TimeUnit.SECONDS.toMillis(5));

                Object response = rabbitTemplate.convertSendAndReceive(
                        inventoryExchange,
                        "inventory.stock.check",
                        request
                );

                Boolean found = null;
                Boolean isAvailable = null;
                Boolean allowBackorder = null;
                Integer availableStock = null;

                // Handle response - could be Map or StockCheckResponse object
                if (response instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> responseMap = (Map<String, Object>) response;
                    found = (Boolean) responseMap.get("found");
                    isAvailable = (Boolean) responseMap.get("isAvailable");
                    allowBackorder = (Boolean) responseMap.get("allowBackorder");
                    availableStock = (Integer) responseMap.getOrDefault("availableStock", 0);
                } else if (response != null && response.getClass().getSimpleName().equals("StockCheckResponse")) {
                    // Handle StockCheckResponse object using reflection to avoid cross-module dependency
                    try {
                        found = (Boolean) response.getClass().getMethod("getFound").invoke(response);
                        isAvailable = (Boolean) response.getClass().getMethod("getIsAvailable").invoke(response);
                        allowBackorder = (Boolean) response.getClass().getMethod("getAllowBackorder").invoke(response);
                        availableStock = (Integer) response.getClass().getMethod("getAvailableStock").invoke(response);
                    } catch (Exception e) {
                        log.error("Error extracting response fields via reflection", e);
                        throw new IllegalStateException("Failed to parse response from inventory module");
                    }
                } else {
                    log.error("Invalid response type while validating stock for product: {}. Response type: {}",
                            item.getProductId(), response != null ? response.getClass().getName() : "null");
                    throw new IllegalStateException("Invalid response from inventory module");
                }

                // Validate the response
                if (Boolean.FALSE.equals(found)) {
                    throw new IllegalArgumentException(
                            String.format("Product %s not found in inventory", item.getProductName())
                    );
                }

                if (!Boolean.TRUE.equals(isAvailable) && !Boolean.TRUE.equals(allowBackorder)) {
                    throw new IllegalArgumentException(
                            String.format("Insufficient stock for product %s. Requested: %d, Available: %d",
                                    item.getProductName(), item.getQuantity(), availableStock != null ? availableStock : 0)
                    );
                }

                log.debug("Stock validation passed for product: {} (Available: {}, Backorder: {})",
                        item.getProductId(), isAvailable, allowBackorder);

            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                log.error("Error validating stock for product: {}", item.getProductId(), e);
                throw new IllegalStateException("Failed to validate stock: " + e.getMessage(), e);
            }
        }

        log.info("All items validated successfully");
    }

    private BigDecimal calculateSubtotal(List<ResellerCartItem> items) {
        return items.stream()
                .map(item -> item.getEffectivePrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal fetchDeliveryCharges(String courierUuid, List<ResellerCartItem> cartItems) {
        log.info("=== FETCHING DELIVERY CHARGES ===");
        log.info("Fetching delivery charges via RabbitMQ for courier: {} with {} items", courierUuid, cartItems.size());

        try {
            // Build cart items data for weight calculation
            List<Map<String, Object>> cartItemsData = cartItems.stream()
                    .map(item -> {
                        Map<String, Object> itemData = new HashMap<>();
                        itemData.put("productId", item.getProductId());
                        itemData.put("variantId", item.getVariantId());
                        itemData.put("quantity", item.getQuantity());
                        return itemData;
                    })
                    .collect(Collectors.toList());

            Map<String, Object> request = new HashMap<>();
            request.put("courierUuid", courierUuid);
            request.put("cartItems", cartItemsData);
            request.put("customerType", "RESELLER");
            request.put("paymentMethodId", null); // COD payment, can be updated if needed

            log.info("=== SENDING REQUEST TO DELIVERY MODULE ===");
            log.info("Exchange: {}", deliveryExchange);
            log.info("Routing Key: reseller.courier.lookup.request");
            log.info("Request data: {}", request);

            rabbitTemplate.setReplyTimeout(TimeUnit.SECONDS.toMillis(5));

            Object response = rabbitTemplate.convertSendAndReceive(
                    deliveryExchange,
                    "reseller.courier.lookup.request",  // Use new routing key
                    request
            );

            log.info("=== RESPONSE RECEIVED FROM DELIVERY MODULE ===");
            log.info("Response received from delivery module: {}", response != null ? response.getClass().getName(): "null");
            log.info("Response content: {}", response);

            Map<String, Object> responseMap = null;
            if (response instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) response;
                responseMap = map;
            } else if (response instanceof byte[]) {
                try {
                    responseMap = objectMapper.readValue((byte[]) response, new TypeReference<Map<String, Object>>() {});
                } catch (Exception e) {
                    log.warn("Failed to deserialize delivery response: {}", e.getMessage());
                    return BigDecimal.valueOf(300);
                }
            }

            if (responseMap != null) {
                Boolean found = (Boolean) responseMap.get("found");
                if (Boolean.TRUE.equals(found)) {
                    Object shippingCostObj = responseMap.get("shippingCost");
                    if (shippingCostObj != null) {
                        BigDecimal shippingCost;
                        if (shippingCostObj instanceof BigDecimal) {
                            shippingCost = (BigDecimal) shippingCostObj;
                        } else if (shippingCostObj instanceof Number) {
                            shippingCost = new BigDecimal(shippingCostObj.toString());
                        } else {
                            shippingCost = BigDecimal.valueOf(300);
                        }
                        log.info("Delivery charges calculated successfully: {}", shippingCost);
                        return shippingCost;
                    }
                }
            }

            log.warn("Invalid response from delivery module, using default charges");
            return BigDecimal.valueOf(300);

        } catch (Exception e) {
            log.error("Error fetching delivery charges via RabbitMQ", e);
            return BigDecimal.valueOf(300); // Fallback
        }
    }

    /**
     * Decreases inventory stock for all order items via the inventory module's stock-decrease RPC.
     * Unlike a plain reservation, this path also keeps the product module's inventory status in
     * sync (e.g. flips it to OUT_OF_STOCK once the sale depletes the stock), matching the POS and
     * online order flows. Throws IllegalStateException on any failure so the order placement
     * transaction rolls back without leaving the order created against unsynced inventory.
     */
    private void decreaseStock(List<ResellerCartItem> items) {
        log.info("Decreasing inventory stock for {} items via RabbitMQ", items.size());

        List<InventoryUpdateRequest.InventoryItem> inventoryItems = items.stream()
                .map(item -> InventoryUpdateRequest.InventoryItem.builder()
                        .productId(item.getProductId())
                        .variantId(item.getVariantId())
                        .quantity(item.getQuantity())
                        .build())
                .collect(Collectors.toList());

        InventoryUpdateRequest request = InventoryUpdateRequest.builder()
                .requestId(java.util.UUID.randomUUID().toString())
                .items(inventoryItems)
                .build();

        try {
            rabbitTemplate.setReplyTimeout(TimeUnit.SECONDS.toMillis(5));

            Object response = rabbitTemplate.convertSendAndReceive(
                    inventoryExchange,
                    "inventory.stock.decrease.request",
                    request
            );

            if (response == null) {
                throw new IllegalStateException("No response from inventory module during stock decrease");
            }

            InventoryUpdateResponse updateResponse = objectMapper.convertValue(response, InventoryUpdateResponse.class);

            if (!Boolean.TRUE.equals(updateResponse.getSuccess())) {
                String reason = buildInventoryFailureReason(updateResponse);
                log.error("Inventory decrease failed for reseller order: {}", reason);
                throw new IllegalStateException("Failed to update inventory: " + reason);
            }

            log.info("Inventory stock decreased and synced for {} items", items.size());

        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error decreasing inventory stock via RabbitMQ", e);
            throw new IllegalStateException("Failed to decrease stock: " + e.getMessage(), e);
        }
    }

    /**
     * Builds a human-readable failure reason from a failed inventory update response,
     * preferring per-item failure details over the generic error message.
     */
    private String buildInventoryFailureReason(InventoryUpdateResponse response) {
        if (response == null) {
            return "No response from inventory module";
        }
        if (response.getFailedItems() != null && !response.getFailedItems().isEmpty()) {
            return response.getFailedItems().stream()
                    .map(item -> String.format("product %d: %s", item.getProductId(), item.getReason()))
                    .collect(Collectors.joining("; "));
        }
        return response.getErrorMessage() != null ? response.getErrorMessage() : "Unknown inventory error";
    }

    private record OrderCreationResult(Long orderId, String orderNumber, String status) {
    }

    private OrderCreationResult createOrderViaRabbitMQ(Reseller reseller, ResellerOrderPlacementRequest request,
                                        List<ResellerCartItem> items, BigDecimal subtotal,
                                        BigDecimal deliveryCharge, BigDecimal total,Long cartId) {
        log.info("Creating order via RabbitMQ for reseller: {} with {} items", reseller.getUuid(), items.size());

        try {
            // Build order items
            List<Map<String, Object>> orderItems = items.stream()
                    .map(item -> {
                        Map<String, Object> itemMap = new HashMap<>();
                        itemMap.put("productId", item.getProductId());
                        itemMap.put("variantId", item.getVariantId());
                        itemMap.put("productName", item.getProductName());
                        itemMap.put("variantName", item.getVariantName());
                        itemMap.put("unitPrice", item.getEffectivePrice());
                        itemMap.put("quantity", item.getQuantity());
                        itemMap.put("totalPrice", item.getTotalPrice());
                        return itemMap;
                    })
                    .collect(Collectors.toList());

            // Build order request
            Map<String, Object> orderRequest = new HashMap<>();
            orderRequest.put("resellerId", reseller.getId());
            orderRequest.put("resellerUuid", reseller.getUuid());
            orderRequest.put("cartId", cartId);
            orderRequest.put("orderType", "RESELLER");
            orderRequest.put("customerName", request.getCustomerName());
            orderRequest.put("customerPhone", request.getCustomerPhone());
            orderRequest.put("customerEmail", request.getCustomerEmail());
            orderRequest.put("shippingAddress", buildShippingAddressMap(request.getShippingAddress()));
            orderRequest.put("items", orderItems);
//            orderRequest.put("cityId", request.getShippingAddress().getCityId());
//            orderRequest.put("districtId", request.getShippingAddress().getDistrictId());
            orderRequest.put("subtotal", subtotal);
            orderRequest.put("deliveryCharges", deliveryCharge);
            orderRequest.put("total", total);
            orderRequest.put("courierUuid", request.getCourierUuid());
            orderRequest.put("notes", request.getNotes());
            orderRequest.put("paymentMethod", "COD");
            orderRequest.put("timestamp", LocalDateTime.now().toString());

            rabbitTemplate.setReplyTimeout(TimeUnit.SECONDS.toMillis(10));

            log.info("=== SENDING ORDER CREATION REQUEST ===");
            log.info("Exchange: {}", orderExchange);
            log.info("Routing Key: order.create.request");
            log.info("Order Request: Reseller UUID: {}, Items: {}", reseller.getUuid(), items.size());

            Object response = rabbitTemplate.convertSendAndReceive(
                    orderExchange,
                    "order.create.request",
                    orderRequest
            );
            log.info("Response received from order module: {}", response != null ? response.getClass().getName() : "null");
            log.info("Response content: {}", response);

            if (response instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> responseMap = (Map<String, Object>) response;

                Boolean success = (Boolean) responseMap.get("success");
                if (Boolean.TRUE.equals(success)) {
                    Long orderId = ((Number) responseMap.get("orderId")).longValue();
                    String orderNumber = (String) responseMap.get("orderNumber");
                    String status = (String) responseMap.get("status");
                    log.info("Order created successfully with ID: {}", orderId);
                    return new OrderCreationResult(orderId, orderNumber, status);
                } else {
                    String error = (String) responseMap.get("error");
                    throw new IllegalStateException("Order creation failed: " + error);
                }
            }

            throw new IllegalStateException("Invalid response from order module");

        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error creating order via RabbitMQ", e);
            throw new IllegalStateException("Failed to create order: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> buildShippingAddressMap(ResellerOrderPlacementRequest.ShippingAddress address) {
        Map<String, Object> addressMap = new HashMap<>();
        addressMap.put("line1", address.getLine1());
        addressMap.put("line2", address.getLine2());
        addressMap.put("city", address.getCity());
        addressMap.put("district", address.getDistrict());
        addressMap.put("cityId", address.getCityId());
        addressMap.put("districtId", address.getDistrictId());
        addressMap.put("province", address.getProvince());
        addressMap.put("postalCode", address.getPostalCode());
        return addressMap;
    }

    private void createPriceOverrides(Long resellerId, Long orderId, List<ResellerCartItem> items) {
        for (ResellerCartItem item : items) {
            if (item.hasOverride()) {
                ResellerPriceOverride override = ResellerPriceOverride.builder()
                        .resellerId(resellerId)
                        .orderId(orderId)
                        .productId(item.getProductId())
                        .variantId(item.getVariantId())
                        .baseUnitPrice(item.getBaseUnitPrice())
                        .overrideUnitPrice(item.getOverrideUnitPrice())
                        .quantity(item.getQuantity())
                        .marginAmount(item.getMarginAmount())
                        .build();
                override.calculateMargin();
                priceOverrideRepository.save(override);
            }
        }
    }

    private void clearCart(ResellerCart cart) {
        cartItemRepository.softDeleteByCartId(cart.getId());
        cart.setIsActive(false);
        cartRepository.save(cart);
    }

    private void publishResellerOrderPlacedEvent(Reseller reseller, Long orderId) {
        log.info("Publishing ResellerOrderPlacedEvent for reseller: {} order: {}", reseller.getUuid(), orderId);

        try {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "RESELLER_ORDER_PLACED");
            event.put("resellerId", reseller.getId());
            event.put("resellerUuid", reseller.getUuid());
            event.put("orderId", orderId);
            event.put("timestamp", LocalDateTime.now().toString());

            rabbitTemplate.convertAndSend(
                    resellerExchange,
                    "reseller.order.placed",
                    event
            );

            log.info("ResellerOrderPlacedEvent published successfully");

        } catch (Exception e) {
            log.error("Error publishing ResellerOrderPlacedEvent", e);
            // Don't throw - event publishing should not fail the order
        }
    }

    private void sendOrderConfirmationEmail(Reseller reseller, Long orderId) {
        log.info("Sending order confirmation email to: {}", reseller.getEmail());
        // TODO: Send email
    }

    private ResellerOrderResponse buildOrderResponse(Long orderId, List<ResellerCartItem> items,
                                                     BigDecimal subtotal, BigDecimal deliveryCharge,
                                                     BigDecimal total) {
        BigDecimal totalProfit = items.stream()
                .map(ResellerCartItem::getMarginAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return ResellerOrderResponse.builder()
                .orderNumber("ORD-" + orderId)
                .subtotal(subtotal)
                .deliveryCharges(deliveryCharge)
                .total(total)
                .totalProfit(totalProfit)
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getOrderStatusHistoryNotes(Long userId, String orderUuid) {
        Reseller reseller = resellerService.getResellerByUserId(userId);

        log.info("Fetching order status history notes for order UUID: {} by reseller: {}",
                orderUuid, reseller.getUuid());

        try {
            // Validate UUID format
            validateUuidFormat(orderUuid);

            // Build RabbitMQ request
            Map<String, Object> request = new HashMap<>();
            request.put("orderUuid", orderUuid);
            request.put("resellerId", reseller.getId());

            rabbitTemplate.setReplyTimeout(TimeUnit.SECONDS.toMillis(5));

            // Send request to order module and wait for response
            Object response = rabbitTemplate.convertSendAndReceive(
                    orderExchange,
                    "order.status.history.notes.lookup.request",
                    request
            );

            if (response instanceof Map) {
                Map<String, Object> responseMap = (Map<String, Object>) response;

                Boolean belongsToReseller = (Boolean) responseMap.get("belongsToReseller");
                if (Boolean.FALSE.equals(belongsToReseller)) {
                    throw new SecurityException("Order does not belong to this reseller");
                }

                // Extract 'notes' list (consumer always returns this field)
                Object notesObj = responseMap.get("notes");
                List<String> notes = new ArrayList<>();
                if (notesObj instanceof List<?> raw) {
                    for (Object o : raw) {
                        if (o != null && !String.valueOf(o).isBlank()) {
                            notes.add(String.valueOf(o));
                        }
                    }
                }

                // If notes is empty, check for reasonForCancellation
                if (notes.isEmpty()) {
                    Object reasonObj = responseMap.get("reasonForCancellation");
                    if (reasonObj != null) {
                        String reasonStr = String.valueOf(reasonObj);
                        if (!reasonStr.isBlank() && !"null".equalsIgnoreCase(reasonStr)) {
                            notes.add(reasonStr);
                            log.info("Added reasonForCancellation to notes for order UUID: {}", orderUuid);
                        }
                    }
                }

                log.info("Returning {} note entries for order UUID: {}", notes.size(), orderUuid);
                return notes;
            }

            log.warn("Invalid response from order service for order notes - order UUID: {}", orderUuid);
            throw new org.psint.beyosclothing.core.exception.ResourceNotFoundException("Order", "uuid", orderUuid);

        } catch (SecurityException e) {
            log.warn("Security exception while fetching order notes {}: {}", orderUuid, e.getMessage());
            throw e;
        } catch (org.psint.beyosclothing.core.exception.ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching order status history notes for UUID: {} by reseller: {}",
                    orderUuid, userId, e);
            throw new RuntimeException("Failed to fetch order status history notes: " + e.getMessage(), e);
        }
    }
}
