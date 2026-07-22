package org.psint.beyosclothing.modules.orders.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.common.dto.PageResponse;
import org.psint.beyosclothing.core.exception.BadRequestException;
import org.psint.beyosclothing.core.exception.ResourceNotFoundException;
import org.psint.beyosclothing.modules.orders.dto.response.AdminOrderDetailResponse;
import org.psint.beyosclothing.modules.orders.dto.response.AdminOrderListResponse;
import org.psint.beyosclothing.modules.orders.entity.OrderEntity;
import org.psint.beyosclothing.modules.orders.entity.OrderItemEntity;
import org.psint.beyosclothing.modules.orders.entity.OrderShippingAddressEntity;
import org.psint.beyosclothing.modules.orders.entity.OrderStatusHistoryEntity;
import org.psint.beyosclothing.modules.orders.repository.*;
import org.psint.beyosclothing.modules.orders.repository.projection.AdminOrderListView;
import org.psint.beyosclothing.modules.orders.service.AdminOrderService;
import org.psint.beyosclothing.modules.pos.entity.PosCustomerEntity;
import org.psint.beyosclothing.modules.pos.repository.PosCartRepository;
import org.psint.beyosclothing.modules.pos.repository.PosCustomerRepository;
import org.psint.beyosclothing.modules.pos.repository.projection.PosCartOrderView;
import org.psint.beyosclothing.modules.resellers.service.ResellerNameLookupService;
import org.psint.beyosclothing.modules.sms.service.OrderSmsNotificationService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Admin Order Service Implementation
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(value = "orderTransactionManager", readOnly = true)
public class AdminOrderServiceImpl implements AdminOrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderShippingAddressRepository shippingAddressRepository;
    private final OrderStatusHistoryRepository statusHistoryRepository;
    private final PosCartRepository posCartRepository;
    private final PosCustomerRepository posCustomerRepository;
    private final RabbitTemplate rabbitTemplate;
    private final OrderSmsNotificationService smsNotificationService;
    private final ResellerNameLookupService resellerNameLookupService;

    /** Status reported for completed POS carts, which have no status column of their own. */
    private static final String POS_CART_COMPLETE_LABEL = "COMPLETE";
    private static final String ORDER_FROM_CUSTOMER = "CUSTOMER";
    private static final String ORDER_FROM_RESELLER = "RESELLER";
    private static final String ORDER_FROM_UNKNOWN = "UNKNOWN";

    @Value("${app.rabbitmq.exchange.delivery:beyos.exchange.delivery}")
    private String deliveryExchange;

    // ─────────────────────────────────────────────────────────────────────────
    // GET ALL ORDERS (paginated + filtered)
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public PageResponse<AdminOrderListResponse> getAllOrders(
            String search,
            OrderEntity.OrderStatus status,
            OrderEntity.OrderSource source,
            String orderFrom,
            LocalDate startDate,
            LocalDate endDate,
            int page,
            int size) {

        log.info("Admin fetching orders - search: {}, status: {}, source: {}, orderFrom: {}, startDate: {}, endDate: {}, page: {}, size: {}",
                search, status, source, orderFrom, startDate, endDate, page, size);

        LocalDateTime startDateTime = (startDate != null) ? startDate.atStartOfDay() : null;
        LocalDateTime endDateTime   = (endDate   != null) ? endDate.atTime(LocalTime.MAX) : null;

        String normalizedSearch    = (search    != null && !search.isBlank())    ? search.trim()    : null;
        String normalizedOrderFrom = (orderFrom != null && !orderFrom.isBlank()) ? orderFrom.trim().toUpperCase() : null;

        long orderCount = orderRepository.countOrdersWithFilters(
                status, source, normalizedOrderFrom, startDateTime, endDateTime, normalizedSearch);

        // Completed POS carts only qualify when the active filters can match a POS / CUSTOMER / COMPLETED order.
        boolean includePosCarts = shouldIncludePosCarts(status, source, normalizedOrderFrom);
        long cartCount = includePosCarts
                ? posCartRepository.countCompletedCartOrders(startDateTime, endDateTime, normalizedSearch)
                : 0;
        long totalElements = orderCount + cartCount;

        // Empty or out-of-range pages are answered from the counts alone - no row fetch, no name RPCs.
        if (size <= 0 || (long) page * size >= totalElements) {
            return buildMergedPageResponse(List.of(), page, size, totalElements);
        }

        List<MergedOrderRow> pageRows = selectPageRows(
                status, source, normalizedOrderFrom, startDateTime, endDateTime, normalizedSearch,
                orderCount, cartCount, page, size);

        return buildMergedPageResponse(mapRowsToResponses(pageRows), page, size, totalElements);
    }

    /**
     * Loads the rows backing the requested page. When only one source has matching rows, the page
     * is fetched directly from the database with normal pagination. The wider in-memory merge
     * window is used only when orders and POS carts both contribute and must be interleaved by
     * creation date.
     */
    private List<MergedOrderRow> selectPageRows(
            OrderEntity.OrderStatus status,
            OrderEntity.OrderSource source,
            String orderFrom,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            String search,
            long orderCount,
            long cartCount,
            int page,
            int size) {

        if (cartCount == 0) {
            return orderRepository.findOrderViewsWithFilters(
                            status, source, orderFrom, startDateTime, endDateTime, search,
                            PageRequest.of(page, size))
                    .stream()
                    .map(view -> new MergedOrderRow(view.getCreatedAt(), view, null))
                    .toList();
        }
        if (orderCount == 0) {
            return posCartRepository.findCompletedCartOrders(
                            startDateTime, endDateTime, search, PageRequest.of(page, size))
                    .stream()
                    .map(view -> new MergedOrderRow(view.getCreatedAt(), null, view))
                    .toList();
        }

        Pageable mergeWindow = PageRequest.of(0, computeMergeWindowSize(page, size));
        List<AdminOrderListView> orderViews = orderRepository.findOrderViewsWithFilters(
                status, source, orderFrom, startDateTime, endDateTime, search, mergeWindow);
        List<PosCartOrderView> cartViews = posCartRepository.findCompletedCartOrders(
                startDateTime, endDateTime, search, mergeWindow);

        List<MergedOrderRow> rows = new ArrayList<>(orderViews.size() + cartViews.size());
        orderViews.forEach(view -> rows.add(new MergedOrderRow(view.getCreatedAt(), view, null)));
        cartViews.forEach(view -> rows.add(new MergedOrderRow(view.getCreatedAt(), null, view)));

        rows.sort(Comparator.comparing(MergedOrderRow::createdAt,
                Comparator.nullsLast(Comparator.reverseOrder())));

        int fromIndex = Math.min(page * size, rows.size());
        int toIndex   = Math.min(fromIndex + size, rows.size());
        return rows.subList(fromIndex, toIndex);
    }

    /**
     * Rows needed per source to build the requested page after the cross-source merge: everything up
     * to and including that page. Capped to guard against overflow on absurd page numbers.
     */
    private int computeMergeWindowSize(int page, int size) {
        long window = (long) (page + 1) * size;
        return (int) Math.clamp(window, 1, Integer.MAX_VALUE);
    }

    /**
     * Completed POS carts represent POS sales made to a customer that are always COMPLETE. They are
     * only relevant when none of the active filters excludes that shape of order.
     */
    private boolean shouldIncludePosCarts(
            OrderEntity.OrderStatus status,
            OrderEntity.OrderSource source,
            String normalizedOrderFrom) {
        boolean statusMatches    = status == null || status == OrderEntity.OrderStatus.COMPLETED;
        boolean sourceMatches    = source == null || source == OrderEntity.OrderSource.POS;
        boolean orderFromMatches = normalizedOrderFrom == null || ORDER_FROM_CUSTOMER.equals(normalizedOrderFrom);
        return statusMatches && sourceMatches && orderFromMatches;
    }

    /**
     * Maps the page's rows to responses. Display names - customer, POS customer and reseller -
     * are resolved for the page only, each in a single batched database query.
     */
    private List<AdminOrderListResponse> mapRowsToResponses(List<MergedOrderRow> pageRows) {
        Map<Long, String> orderCustomerNames = resolveOrderCustomerNames(pageRows);
        Map<Long, String> cartCustomerNames  = resolveCartCustomerNames(pageRows);
        Map<Long, String> resellerNames      = resolveResellerNames(pageRows);

        return pageRows.stream()
                .map(row -> row.order() != null
                        ? mapOrderViewToResponse(row.order(), orderCustomerNames, resellerNames)
                        : mapCartViewToResponse(row.cart(), cartCustomerNames))
                .toList();
    }

    /**
     * Batch-loads customer names for the page's online orders from their shipping addresses, keyed by
     * order id. Restores the pre-projection behaviour without reintroducing a per-order query.
     */
    private Map<Long, String> resolveOrderCustomerNames(List<MergedOrderRow> pageRows) {
        List<Long> orderIds = pageRows.stream()
                .map(MergedOrderRow::order)
                .filter(Objects::nonNull)
                .map(AdminOrderListView::getId)
                .filter(Objects::nonNull)
                .toList();
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>();
        for (OrderShippingAddressEntity address : shippingAddressRepository.findByOrderIdIn(orderIds)) {
            names.put(address.getOrderId(), address.getFullName());
        }
        return names;
    }

    /**
     * Batch-loads customer names for the page's POS carts from pos_customers, keyed by customer id.
     * Walk-in carts have no customer id and are therefore absent from the returned map.
     */
    private Map<Long, String> resolveCartCustomerNames(List<MergedOrderRow> pageRows) {
        List<Long> customerIds = pageRows.stream()
                .map(MergedOrderRow::cart)
                .filter(Objects::nonNull)
                .map(PosCartOrderView::getCustomerId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (customerIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>();
        for (PosCustomerEntity customer : posCustomerRepository.findAllById(customerIds)) {
            names.put(customer.getId(), customer.getFullName());
        }
        return names;
    }

    /**
     * Resolves reseller names for the distinct resellers appearing on the current page with a single
     * batched database query - no RabbitMQ round-trips.
     */
    private Map<Long, String> resolveResellerNames(List<MergedOrderRow> pageRows) {
        Set<Long> resellerIds = pageRows.stream()
                .map(MergedOrderRow::order)
                .filter(Objects::nonNull)
                .map(AdminOrderListView::getResellerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (resellerIds.isEmpty()) {
            return Map.of();
        }
        return resellerNameLookupService.getResellerNames(resellerIds);
    }

    private PageResponse<AdminOrderListResponse> buildMergedPageResponse(
            List<AdminOrderListResponse> content, int page, int size, long totalElements) {
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
        return PageResponse.<AdminOrderListResponse>builder()
                .content(content)
                .pageNumber(page)
                .pageSize(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .last(page >= totalPages - 1)
                .first(page == 0)
                .empty(content.isEmpty())
                .build();
    }

    private AdminOrderListResponse mapOrderViewToResponse(
            AdminOrderListView view,
            Map<Long, String> orderCustomerNames,
            Map<Long, String> resellerNames) {
        String orderFrom;
        String orderFromName = null;
        if (view.getResellerId() != null) {
            orderFrom = ORDER_FROM_RESELLER;
            orderFromName = resellerNames.get(view.getResellerId());
        } else if (view.getCustomerId() != null) {
            orderFrom = ORDER_FROM_CUSTOMER;
        } else {
            orderFrom = ORDER_FROM_UNKNOWN;
        }

        String customerName = orderCustomerNames.get(view.getId());

        return AdminOrderListResponse.builder()
                .orderUuid(view.getUuid())
                .orderNumber(view.getOrderNumber())
                .orderDate(view.getCreatedAt())
                .customerName(customerName != null ? customerName : "N/A")
                .subtotal(view.getSubtotal())
                .total(view.getTotal())
                .orderType(view.getSource() != null ? view.getSource().name() : null)
                .orderFrom(orderFrom)
                .orderFromName(orderFromName)
                .status(view.getStatus() != null ? view.getStatus().name() : null)
                .paymentStatus(view.getPaymentStatus() != null ? view.getPaymentStatus().name() : null)
                .build();
    }

    private AdminOrderListResponse mapCartViewToResponse(PosCartOrderView view, Map<Long, String> cartCustomerNames) {
        String customerName = view.getCustomerId() != null ? cartCustomerNames.get(view.getCustomerId()) : null;
        return AdminOrderListResponse.builder()
                .orderUuid(view.getUuid())
                .orderNumber(view.getUuid())
                .orderDate(view.getCreatedAt())
                .customerName(customerName != null ? customerName : "Walk-in")
                .subtotal(view.getSubtotal())
                .total(view.getTotal())
                .orderType("POS SHOP")
                .orderFrom(ORDER_FROM_CUSTOMER)
                .orderFromName(null)
                .status(POS_CART_COMPLETE_LABEL)
                .paymentStatus(OrderEntity.PaymentStatus.PAID.name())
                .build();
    }

    /** Lightweight row wrapper holding either an order or a completed POS cart projection for merged sorting. */
    private record MergedOrderRow(LocalDateTime createdAt, AdminOrderListView order, PosCartOrderView cart) {
    }

    @Override
    public PageResponse<AdminOrderListResponse> getPendingOrders(
            OrderEntity.OrderSource orderType,
            String orderFrom,
            int page,
            int size) {
        log.info("Admin fetching pending orders - orderType: {}, orderFrom: {}, page: {}, size: {}",
                orderType, orderFrom, page, size);

        Pageable pageable = PageRequest.of(page, size);
        String normalizedOrderFrom = (orderFrom != null && !orderFrom.isBlank())
                ? orderFrom.trim().toUpperCase()
                : null;

        Page<OrderEntity> pendingPage = orderRepository.findAllWithFilters(
                OrderEntity.OrderStatus.PENDING,
                orderType,
                normalizedOrderFrom,
                null,
                null,
                null,
                pageable
        );

        return toAdminOrderListPageResponse(pendingPage);
    }

    private AdminOrderListResponse mapToAdminListResponse(OrderEntity order) {
        String customerName = shippingAddressRepository.findByOrderId(order.getId())
                .map(OrderShippingAddressEntity::getFullName)
                .orElse("N/A");

        String orderFrom;
        String orderFromName = null;

        if (order.getResellerId() != null) {
            orderFrom = "RESELLER";
            orderFromName = resellerNameLookupService.getResellerName(order.getResellerId());
        } else if (order.getCustomerId() != null) {
            orderFrom = "CUSTOMER";
        } else {
            orderFrom = "UNKNOWN";
        }

        return AdminOrderListResponse.builder()
                .orderUuid(order.getUuid())
                .orderNumber(order.getOrderNumber())
                .orderDate(order.getCreatedAt())
                .customerName(customerName)
                .subtotal(order.getSubtotal())
                .total(order.getTotal())
                .orderType(order.getSource() != null ? order.getSource().name() : null)
                .orderFrom(orderFrom)
                .orderFromName(orderFromName)
                .status(order.getStatus() != null ? order.getStatus().name() : null)
                .paymentStatus(order.getPaymentStatus() != null ? order.getPaymentStatus().name() : null)
                .build();
    }

    // Helper to convert a Page<OrderEntity> into a PageResponse<AdminOrderListResponse>
    private PageResponse<AdminOrderListResponse> toAdminOrderListPageResponse(Page<OrderEntity> ordersPage) {
        Page<AdminOrderListResponse> responsePage = ordersPage.map(this::mapToAdminListResponse);

        return PageResponse.<AdminOrderListResponse>builder()
                .content(responsePage.getContent())
                .pageNumber(responsePage.getNumber())
                .pageSize(responsePage.getSize())
                .totalElements(responsePage.getTotalElements())
                .totalPages(responsePage.getTotalPages())
                .last(responsePage.isLast())
                .first(responsePage.isFirst())
                .empty(responsePage.isEmpty())
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET ORDER DETAIL BY UUID
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public AdminOrderDetailResponse getOrderDetail(String orderUuid) {
        return resolveAdminOrderDetailResponse(orderUuid, false);
    }

    @Override
    public AdminOrderDetailResponse getPendingOrderDetail(String orderUuid) {
        return resolveAdminOrderDetailResponse(orderUuid, true);
    }

    @Override
    @Transactional(value = "orderTransactionManager")
    public AdminOrderDetailResponse rejectOrder(String orderUuid, String rejectionReason, String adminNotes) {
        log.info("Admin rejecting order - uuid: {}, reason: {}", orderUuid, rejectionReason);

        OrderEntity order = orderRepository.findByUuid(orderUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "uuid", orderUuid));

        if (order.getStatus() == OrderEntity.OrderStatus.REJECTED) {
            throw new BadRequestException("Order is already rejected");
        }
        if (order.getStatus() == OrderEntity.OrderStatus.DELIVERED ||
                order.getStatus() == OrderEntity.OrderStatus.COMPLETED) {
            throw new BadRequestException("Cannot reject an order that is already delivered or completed");
        }

        String oldStatus = order.getStatus() != null ? order.getStatus().name() : null;

        order.setStatus(OrderEntity.OrderStatus.REJECTED);
        if (adminNotes != null && !adminNotes.isBlank()) {
            String existing = order.getNotes() != null ? order.getNotes() + "\n" : "";
            order.setNotes(existing + adminNotes);
        }

        order = orderRepository.save(order);

        // record status history
        OrderStatusHistoryEntity history = OrderStatusHistoryEntity.builder()
                .orderId(order.getId())
                .oldStatus(oldStatus)
                .newStatus(OrderEntity.OrderStatus.REJECTED.name())
                .notes(rejectionReason != null ? rejectionReason : adminNotes)
                .build();
        statusHistoryRepository.save(history);
        log.info("Order rejected - orderNumber: {}, uuid: {}", order.getOrderNumber(), order.getUuid());

        notifyOrderStatusChange(order);

        return buildAdminOrderDetailResponse(order);
    }

    @Override
    @Transactional(value = "orderTransactionManager")
    public AdminOrderDetailResponse updateOrderStatus(String orderUuid, OrderEntity.OrderStatus newStatus, String notes) {
        log.info("Admin updating order status - uuid: {}, newStatus: {}", orderUuid, newStatus);

        OrderEntity order = orderRepository.findByUuid(orderUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "uuid", orderUuid));

        String oldStatus = order.getStatus() != null ? order.getStatus().name() : null;
        if (newStatus.name().equals(oldStatus)) {
            throw new BadRequestException("Order is already in status: " + newStatus);
        }

        order.setStatus(newStatus);
        if (notes != null && !notes.isBlank()) {
            String existing = order.getNotes() != null ? order.getNotes() + "\n" : "";
            order.setNotes(existing + notes);
        }

        order = orderRepository.save(order);

        OrderStatusHistoryEntity history = OrderStatusHistoryEntity.builder()
                .orderId(order.getId())
                .oldStatus(oldStatus)
                .newStatus(newStatus.name())
                .notes(notes)
                .build();
        statusHistoryRepository.save(history);
        log.info("Order status updated - orderNumber: {}, uuid: {}, {} -> {}",
                order.getOrderNumber(), order.getUuid(), oldStatus, newStatus);

        notifyOrderStatusChange(order);

        return buildAdminOrderDetailResponse(order);
    }

    /**
     * Notifies the order's owner (customer, POS customer or reseller) by SMS of the current status.
     * Recipient resolution and sending are delegated to OrderSmsNotificationService.
     */
    private void notifyOrderStatusChange(OrderEntity order) {
        String status = order.getStatus() != null ? order.getStatus().name() : null;
        smsNotificationService.sendOrderStatusUpdateForOrder(order.getUuid(), status);
    }

    private AdminOrderDetailResponse resolveAdminOrderDetailResponse(String orderUuid, boolean pendingOnly) {
        log.info("Admin fetching {}order detail for UUID: {}", pendingOnly ? "pending " : "", orderUuid);

        OrderEntity order = orderRepository.findByUuid(orderUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "uuid", orderUuid));

        if (pendingOnly && order.getStatus() != OrderEntity.OrderStatus.PENDING) {
            throw new BadRequestException("Order is not in PENDING status");
        }

        return buildAdminOrderDetailResponse(order);
    }

    private AdminOrderDetailResponse buildAdminOrderDetailResponse(OrderEntity order) {

        // ── 1. Shipping / Customer info ──────────────────────────────────────
        OrderShippingAddressEntity address = shippingAddressRepository
                .findByOrderId(order.getId()).orElse(null);

        AdminOrderDetailResponse.CustomerInfo customerInfo = buildCustomerInfo(address);

        // ── 2. Order items ───────────────────────────────────────────────────
        List<AdminOrderDetailResponse.OrderItemInfo> items =
                buildCustomerOrderItems(orderItemRepository.findByOrderId(order.getId()));

        boolean isResellerOrder = order.getResellerId() != null;


        // ── 3. Payment info ──────────────────────────────────────────────────
        AdminOrderDetailResponse.PaymentInfo paymentInfo = AdminOrderDetailResponse.PaymentInfo.builder()
                .paymentMethod(order.getPaymentMethod())
                .paymentStatus(order.getPaymentStatus() != null ? order.getPaymentStatus().name() : null)
                .build();

        // ── 4. Tracking info via RabbitMQ ────────────────────────────────────
        AdminOrderDetailResponse.TrackingInfo trackingInfo = fetchTrackingInfo(order.getId());

        // ── 5. Order history ─────────────────────────────────────────────────
        List<AdminOrderDetailResponse.OrderHistoryEntry> history = buildOrderHistory(
                statusHistoryRepository.findByOrderIdOrderByCreatedAtDesc(order.getId()));

        // ── 6. Determine orderFrom label ─────────────────────────────────────
        String orderFrom = isResellerOrder ? "RESELLER"
                : order.getCustomerId() != null ? "CUSTOMER" : "UNKNOWN";

        return AdminOrderDetailResponse.builder()
                .orderUuid(order.getUuid())
                .orderNumber(order.getOrderNumber())
                .orderDate(order.getCreatedAt())
                .orderStatus(order.getStatus() != null ? order.getStatus().name() : null)
                .orderFrom(orderFrom)
                .orderType(order.getSource() != null ? order.getSource().name() : null)
                .transactionId(order.getPaymentReference())
                .customer(customerInfo)
                .items(items)
                .subtotal(order.getSubtotal())
                .shippingCost(order.getShippingCost())
                .discountTotal(order.getDiscountTotal())
                .tax(BigDecimal.ZERO)
                .total(order.getTotal())
                .payment(paymentInfo)
                .tracking(trackingInfo)
                .orderHistory(history)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private AdminOrderDetailResponse.CustomerInfo buildCustomerInfo(OrderShippingAddressEntity a) {
        if (a == null) return null;
        return AdminOrderDetailResponse.CustomerInfo.builder()
                .fullName(a.getFullName())
                .phone(a.getPhone())
                .email(a.getEmail())
                .addressLine1(a.getAddressLine1())
                .addressLine2(a.getAddressLine2())
                .city(a.getCity())
                .province(a.getProvince())
                .postalCode(a.getPostalCode())
                .country(a.getCountry())
                .build();
    }

    private List<AdminOrderDetailResponse.OrderItemInfo> buildCustomerOrderItems(List<OrderItemEntity> entities) {
        return entities.stream().map(e -> AdminOrderDetailResponse.OrderItemInfo.builder()
                .productName(e.getProductTitle())
                .variantName(e.getVariantTitle())
                .unitPrice(e.getUnitPrice())
                .quantity(e.getQuantity())
                .totalPrice(e.getTotalPrice())
                .isRefunded(e.getIsRefunded())
                .basePrice(e.getUnitBasePrice())
                .margin(e.getResellerMarginAmount())
                .build()
        ).collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private AdminOrderDetailResponse.TrackingInfo fetchTrackingInfo(Long orderId) {
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

            if (response instanceof Map) {
                Map<String, Object> responseMap = (Map<String, Object>) response;
                Boolean found = (Boolean) responseMap.get("found");

                if (Boolean.TRUE.equals(found)) {
                    String shippedAt    = (String) responseMap.get("shippedAt");
                    String deliveredAt  = (String) responseMap.get("deliveredAt");
                    String expectedDelivery = buildExpectedDelivery(shippedAt, deliveredAt);

                    return AdminOrderDetailResponse.TrackingInfo.builder()
                            .carrier((String) responseMap.get("carrier"))
                            .trackingNumber((String) responseMap.get("trackingNumber"))
                            .trackingUrl((String) responseMap.get("trackingUrl"))
                            .expectedDelivery(expectedDelivery)
                            .build();
                }
            }
        } catch (Exception e) {
            log.warn("Could not fetch tracking info for orderId={}: {}", orderId, e.getMessage());
        }

        return null;
    }

    private String buildExpectedDelivery(String shippedAt, String deliveredAt) {
        if (shippedAt == null && deliveredAt == null) return null;
        if (shippedAt != null && deliveredAt != null) {
            return shippedAt.substring(0, 10) + " - " + deliveredAt.substring(0, 10);
        }
        return deliveredAt != null ? deliveredAt.substring(0, 10) : shippedAt.substring(0, 10);
    }

    private List<AdminOrderDetailResponse.OrderHistoryEntry> buildOrderHistory(
            List<OrderStatusHistoryEntity> histories) {
        return histories.stream().map(h -> AdminOrderDetailResponse.OrderHistoryEntry.builder()
                .status(h.getNewStatus())
                .description(h.getNotes())
                .timestamp(h.getCreatedAt())
                .build()
        ).collect(Collectors.toList());
    }

}
