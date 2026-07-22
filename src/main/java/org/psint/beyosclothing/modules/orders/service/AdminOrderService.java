package org.psint.beyosclothing.modules.orders.service;

import org.psint.beyosclothing.common.dto.PageResponse;
import org.psint.beyosclothing.modules.orders.dto.response.AdminOrderDetailResponse;
import org.psint.beyosclothing.modules.orders.dto.response.AdminOrderListResponse;
import org.psint.beyosclothing.modules.orders.entity.OrderEntity;

import java.time.LocalDate;

/**
 * Admin Order Service Interface
 */
public interface AdminOrderService {

    /**
     * Get all orders with filters - Admin only
     *
     * @param search     search term matched against order number, customer name, phone, email
     * @param status     filter by OrderStatus
     * @param source     filter by OrderSource (ONLINE / POS)
     * @param orderFrom  filter by order origin: CUSTOMER or RESELLER
     * @param startDate  inclusive start date filter on created_at
     * @param endDate    inclusive end date filter on created_at
     * @param page       zero-based page index
     * @param size       page size
     * @return paginated list of AdminOrderListResponse
     */
    PageResponse<AdminOrderListResponse> getAllOrders(
            String search,
            OrderEntity.OrderStatus status,
            OrderEntity.OrderSource source,
            String orderFrom,
            LocalDate startDate,
            LocalDate endDate,
            int page,
            int size
    );

    /**
     * Get paginated list of pending orders with optional filters - Admin only
     *
     * @param orderType filter by order type (ONLINE / POS)
     * @param orderFrom filter by order origin (CUSTOMER / RESELLER)
     * @param page zero-based page index
     * @param size page size
     * @return paginated list of pending AdminOrderListResponse
     */
    PageResponse<AdminOrderListResponse> getPendingOrders(
            OrderEntity.OrderSource orderType,
            String orderFrom,
            int page,
            int size
    );

    /**
     * Get full order detail by UUID - Admin only
     *
     * @param orderUuid order UUID
     * @return AdminOrderDetailResponse
     */
    AdminOrderDetailResponse getOrderDetail(String orderUuid);

    /**
     * Get full order detail by UUID when order status is PENDING - Admin only
     *
     * @param orderUuid order UUID
     * @return AdminOrderDetailResponse for pending order
     */
    AdminOrderDetailResponse getPendingOrderDetail(String orderUuid);

    /**
     * Reject an order (Admin only). Sets order status to REJECT and records reason/notes.
     * @param orderUuid order UUID to reject
     * @param rejectionReason short reason for rejection (displayed to customer)
     * @param adminNotes optional admin notes stored on the order
     * @return updated AdminOrderDetailResponse
     */
    AdminOrderDetailResponse rejectOrder(String orderUuid, String rejectionReason, String adminNotes);

    /**
     * Update an order's status (Admin only). Records the change in status history and notifies
     * the order's owner - the customer (CUSTOMER/ONLINE or POS) or the reseller (RESELLER) - by SMS.
     * @param orderUuid order UUID to update
     * @param newStatus the status to move the order to
     * @param notes optional admin notes stored on the order
     * @return updated AdminOrderDetailResponse
     */
    AdminOrderDetailResponse updateOrderStatus(String orderUuid, OrderEntity.OrderStatus newStatus, String notes);
}