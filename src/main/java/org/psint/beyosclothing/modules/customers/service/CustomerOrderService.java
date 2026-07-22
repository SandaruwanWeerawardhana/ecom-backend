package org.psint.beyosclothing.modules.customers.service;


import org.psint.beyosclothing.modules.customers.dto.request.AddToCartRequestDTO;
import org.psint.beyosclothing.modules.customers.dto.request.CreateWishlistItemRequest;
import org.psint.beyosclothing.modules.customers.dto.request.PlaceOrderRequest;
import org.psint.beyosclothing.modules.customers.dto.request.UpdateOrderPaymentStatusRequest;
import org.psint.beyosclothing.modules.customers.dto.response.CartResponseDTO;
import org.psint.beyosclothing.modules.customers.dto.response.CustomerDashboardCountsResponse;
import org.psint.beyosclothing.modules.customers.dto.response.OrderDetailResponse;
import org.psint.beyosclothing.modules.customers.dto.response.OrderPaymentStatusResponse;
import org.psint.beyosclothing.modules.customers.dto.response.WishlistItemResponse;

import java.util.List;

/**
 * Customer Order Service Interface
 * Handles customer-scoped order operations
 */
public interface CustomerOrderService {

    /**
     * Create an order for a customer
     * @param request Order placement request
     * @param customerUuid Customer UUID
     * @return Order placement response
     */
    OrderDetailResponse createOrder(PlaceOrderRequest request, String customerUuid);

    /**
     * Get all order details for a customer
     * @param customerUuid Customer UUID (for authorization)
     * @return Order detail responses
     */
    List<OrderDetailResponse> getOrderById(String customerUuid);

    /**
     * Get the current payment/order status for a single order. Used by the frontend to poll for the
     * outcome of an online payment after the customer returns from the gateway.
     * @param orderUuid Order UUID to check
     * @param customerUuid Customer UUID (for authorization/ownership)
     * @return Order and payment status snapshot
     */
    OrderPaymentStatusResponse getOrderPaymentStatus(String orderUuid, String customerUuid);

    /**
     * Update/re-verify an order's payment status and return the refreshed snapshot. The client may
     * report the status it observed (e.g. UNPAID after an abandoned checkout) but can never set PAID;
     * the gateway verification result always takes precedence over the client-reported status, and a
     * payment newly confirmed as PAID also triggers the deferred order finalization.
     * @param orderUuid Order UUID to update
     * @param customerUuid Customer UUID (for authorization/ownership)
     * @param request Client-reported payment status
     * @return Refreshed order and payment status snapshot
     */
    OrderPaymentStatusResponse updateOrderPaymentStatus(String orderUuid, String customerUuid,
                                                        UpdateOrderPaymentStatusRequest request);

    /**
     * Create (or get existing) cart for a customer and optionally add an item
     * @param customerUuid Customer UUID
     * @param request Optional add-to-cart request (null to just initialize cart)
     * @return Cart response
     */
    CartResponseDTO createCart(String customerUuid, AddToCartRequestDTO request);

    /**
     * Get cart by its UUID
     * @param cartUuid Cart UUID
     * @param customerUuid Customer UUID (for authorization)
     * @return Cart response
     */
    CartResponseDTO getCartById(String cartUuid, String customerUuid);

    /**
     * Add a product to a customer's wishlist
     * @param customerUuid Customer UUID
     * @param request Wishlist item request
     * @return Created wishlist item response
     */
    WishlistItemResponse createWishlistItem(String customerUuid, CreateWishlistItemRequest request);

    /**
     * Get wishlist items for a customer
     * @param customerUuid Customer UUID
     * @return Wishlist items owned by the customer
     */
    List<WishlistItemResponse> getWishlistItemsByCustomerUuid(String customerUuid);

    /**
     * Get a wishlist item by UUID
     * @param wishlistItemUuid Wishlist item UUID
     * @param customerUuid Customer UUID (for authorization)
     * @return Wishlist item response
     */
    WishlistItemResponse getWishlistItemByUuid(String wishlistItemUuid, String customerUuid);

    /**
     * Soft delete a wishlist item by setting isAvailable to false
     * @param wishlistItemUuid Wishlist item UUID
     * @param customerUuid Customer UUID (for authorization)
     */
    void deleteWishlistItem(String wishlistItemUuid, String customerUuid);

    /**
     * Get customer dashboard counts
     * @param customerUuid Customer UUID
     * @return Order and wishlist counts for the customer
     */
    CustomerDashboardCountsResponse getCustomerDashboardCounts(String customerUuid);
}
