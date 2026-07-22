package org.psint.beyosclothing.modules.resellers.service;

import org.psint.beyosclothing.common.dto.PageResponse;
import org.psint.beyosclothing.modules.resellers.dto.request.ResellerOrderPlacementRequest;
import org.psint.beyosclothing.modules.resellers.dto.response.ResellerOrderDetailResponse;
import org.psint.beyosclothing.modules.resellers.dto.response.ResellerOrderListResponse;
import org.psint.beyosclothing.modules.resellers.dto.response.ResellerOrderResponse;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

/**
 * Service interface for Reseller Order operations
 */
public interface ResellerOrderService {

    /**
     * Place order for reseller's customer
     * @param userId User ID
     * @param request Order placement request
     * @return Order response with profit details
     */
    ResellerOrderResponse placeOrder(Long userId, ResellerOrderPlacementRequest request);

    /**
     * Get orders for reseller (legacy method)
     * @param userId User ID
     * @param pageable Pagination
     * @return Paginated order list
     */
    ResellerOrderListResponse getOrders(Long userId, Pageable pageable);

    /**
     * Get orders for reseller with advanced filters
     * @param userId User ID
     * @param search Search by order number or customer name (optional)
     * @param status Filter by order status (optional)
     * @param startDate Filter orders from this date (optional)
     * @param endDate Filter orders up to this date (optional)
     * @param page Zero-based page index
     * @param size Page size
     * @return Paginated order list with filters applied
     */
    PageResponse<ResellerOrderListResponse.OrderSummary> getOrdersWithFilters(
            Long userId,
            String search,
            String status,
            LocalDate startDate,
            LocalDate endDate,
            int page,
            int size
    );

    /**
     * Get orders for a reseller by reseller UUID with advanced filters
     * @param resellerUuid Reseller UUID from frontend
     * @param search Search by order number or customer name (optional)
     * @param status Filter by order status (optional)
     * @param startDate Filter orders from this date (optional)
     * @param endDate Filter orders up to this date (optional)
     * @param page Zero-based page index
     * @param size Page size
     * @return Paginated order list with filters applied
     */
    PageResponse<ResellerOrderListResponse.OrderSummary> getOrdersByResellerUuid(
            String resellerUuid,
            String search,
            String status,
            LocalDate startDate,
            LocalDate endDate,
            int page,
            int size
    );

    /**
     * Get order by UUID
     * @param userId User ID
     * @param orderUuid Order UUID
     * @return Order details
     */
    ResellerOrderDetailResponse getOrderByUuid(Long userId, String orderUuid);

    /**
     * Get pending orders
     * @param userId User ID
     * @param pageable Pagination
     * @return Pending orders
     */
    ResellerOrderListResponse getPendingOrders(Long userId, Pageable pageable);

    /**
     * Get pending orders for reseller with advanced filters
     * Filters for PENDING, PROCESSING, and OUT_FOR_DELIVERY statuses only
     * @param userId User ID
     * @param search Search by order number or customer name (optional)
     * @param startDate Filter orders from this date (optional)
     * @param endDate Filter orders up to this date (optional)
     * @param page Zero-based page index
     * @param size Page size
     * @return Paginated pending order list with filters applied
     */
    PageResponse<ResellerOrderListResponse.OrderSummary> getPendingOrdersWithFilters(
            Long userId,
            String search,
            LocalDate startDate,
            LocalDate endDate,
            int page,
            int size
    );

    /**
     * Get order status history notes
     * Fetches all status change history with notes for a specific order
     * @param userId User ID
     * @param orderUuid Order UUID
     */
    List<String> getOrderStatusHistoryNotes(Long userId, String orderUuid);
}
