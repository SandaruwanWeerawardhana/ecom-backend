package org.psint.beyosclothing.modules.cart.service;

import org.psint.beyosclothing.modules.cart.dto.request.AddToCartRequestDTO;
import org.psint.beyosclothing.modules.cart.dto.request.ApplyPromoCodeRequestDTO;
import org.psint.beyosclothing.modules.cart.dto.request.UpdateCartItemRequestDTO;
import org.psint.beyosclothing.modules.cart.dto.response.CartResponseDTO;

/**
 * Cart Service Interface
 * Handles all cart operations for both guest and customer carts
 */
public interface CartService {

    /**
     * Get cart for customer or guest
     * @param customerUuid Customer UUID (null for guest)
     * @param guestSessionToken Guest session token (null for customer)
     * @return Cart details
     */
    CartResponseDTO getCart(String customerUuid, String guestSessionToken);

    /**
     * Add item to cart
     * @param customerUuid Customer UUID (null for guest)
     * @param guestSessionToken Guest session token (null for customer)
     * @param request Add to cart request (contains product UUID and optional variant UUID)
     * @return Updated cart
     */
    CartResponseDTO addToCart(String customerUuid, String guestSessionToken, AddToCartRequestDTO request);

    /**
     * Update cart item quantity
     * @param customerUuid Customer UUID (null for guest)
     * @param guestSessionToken Guest session token (null for customer)
     * @param itemUuid Cart item UUID
     * @param request Update request
     * @return Updated cart
     */
    CartResponseDTO updateCartItem(String customerUuid, String guestSessionToken, String itemUuid, UpdateCartItemRequestDTO request);

    /**
     * Remove item from cart
     * @param customerUuid Customer UUID (null for guest)
     * @param guestSessionToken Guest session token (null for customer)
     * @param itemUuid Cart item UUID
     * @return Updated cart
     */
    CartResponseDTO removeCartItem(String customerUuid, String guestSessionToken, String itemUuid);

    /**
     * Apply promo code to cart
     * @param customerUuid Customer UUID (null for guest)
     * @param guestSessionToken Guest session token (null for customer)
     * @param request Promo code request
     * @return Updated cart with discount applied
     */
    CartResponseDTO applyPromoCode(String customerUuid, String guestSessionToken, ApplyPromoCodeRequestDTO request);


    /**
     * Remove promo code from cart
     * @param customerUuid Customer UUID (null for guest)
     * @param guestSessionToken Guest session token (null for customer)
     * @return Updated cart
     */
    CartResponseDTO removePromoCode(String customerUuid, String guestSessionToken);

    /**
     * Clear entire cart
     * @param customerUuid Customer UUID (null for guest)
     * @param guestSessionToken Guest session token (null for customer)
     */
    void clearCart(String customerUuid, String guestSessionToken);

    /**
     * Merge guest cart with customer cart after login
     * @param customerUuid Customer UUID after login
     * @param guestSessionToken Guest session token before login
     * @return Merged cart
     */
    CartResponseDTO mergeGuestCartToCustomer(String customerUuid, String guestSessionToken);

    /**
     * Generate guest session token for new guest user
     * @return Secure guest session token
     */
    String generateGuestSessionToken();

    /**
     * Expire old guest carts (scheduled job)
     */
    void expireOldGuestCarts();
}
