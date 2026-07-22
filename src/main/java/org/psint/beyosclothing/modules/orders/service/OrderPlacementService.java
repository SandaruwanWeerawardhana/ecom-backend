package org.psint.beyosclothing.modules.orders.service;

import org.psint.beyosclothing.modules.orders.dto.request.PlaceOrderRequest;
import org.psint.beyosclothing.modules.orders.dto.response.OrderDetailResponse;
import org.psint.beyosclothing.modules.orders.dto.response.OrderPlacementResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Order Placement Service Interface
 */
public interface OrderPlacementService {

    /**
     * Place an order
     * @param request Order placement request
     * @param customerUuid Customer UUID (from authentication)
     * @param guestSessionToken Guest session token (if guest)
     * @return Order placement response
     */
    OrderPlacementResponse placeOrder(PlaceOrderRequest request, String customerUuid, String guestSessionToken);

    /**
     * Get order details by UUID
     * @param orderUuid Order UUID
     * @param customerUuid Customer UUID (for authorization)
     * @return Order details
     */
    OrderDetailResponse getOrderDetails(String orderUuid, String customerUuid);

    /**
     * Get customer's orders
     * @param customerUuid Customer UUID
     * @param pageable Pagination
     * @return Page of orders
     */
    Page<OrderDetailResponse> getCustomerOrders(String customerUuid, Pageable pageable);
}

