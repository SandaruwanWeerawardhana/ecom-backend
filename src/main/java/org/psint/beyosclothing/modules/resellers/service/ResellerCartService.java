package org.psint.beyosclothing.modules.resellers.service;

import org.psint.beyosclothing.modules.resellers.dto.request.AddToCartRequest;
import org.psint.beyosclothing.modules.resellers.dto.request.UpdateCartItemRequest;
import org.psint.beyosclothing.modules.resellers.dto.response.ResellerCartResponse;

/**
 * Service interface for Reseller Cart operations
 */
public interface ResellerCartService {

    /**
     * Get or create cart for reseller
     * @param resellerId Reseller ID
     * @return Cart UUID
     */
    String getOrCreateCart(Long resellerId);

    /**
     * Add item to cart
     * @param resellerId Reseller ID
     * @param request Add to cart request
     * @return Updated cart
     */
    ResellerCartResponse addToCart(Long resellerId, AddToCartRequest request);

    /**
     * Update cart item
     * @param resellerId Reseller ID
     * @param request Update request
     * @return Updated cart
     */
    ResellerCartResponse updateCartItem(Long resellerId, UpdateCartItemRequest request);

    /**
     * Remove cart item
     * @param resellerId Reseller ID
     * @param cartItemUuid Cart item UUID
     * @return Updated cart
     */
    ResellerCartResponse removeCartItem(Long resellerId, String cartItemUuid);

    /**
     * Clear cart
     * @param resellerId Reseller ID
     */
    void clearCart(Long resellerId);

    /**
     * Get cart with all items
     * @param resellerId Reseller ID
     * @return Cart response
     */
    ResellerCartResponse getCart(Long resellerId);

    /**
     * Get cart item count
     * @param resellerId Reseller ID
     * @return Item count
     */
    Long getCartItemCount(Long resellerId);
}

