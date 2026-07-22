package org.psint.beyosclothing.modules.payment.service;

import org.psint.beyosclothing.modules.payment.dto.request.CheckoutRequest;
import org.psint.beyosclothing.modules.payment.dto.response.CheckoutResponse;
import org.psint.beyosclothing.modules.payment.dto.response.PaymentMethodResponse;

import java.util.List;

/**
 * Checkout Service Interface
 * Handles checkout preparation and calculation
 */
public interface CheckoutService {

    /**
     * Prepare checkout summary with all required data
     * Aggregates data from multiple modules:
     * - Cart items from Cart module
     * - Customer address from Customer module
     * - Shipping cost from Delivery module
     * - Payment method details from Payment module
     *
     * @param customerUuid Customer UUID (null for guest)
     * @param guestSessionToken Guest session token (null for customer)
     * @param request Checkout request with courier and payment method selection
     * @return Complete checkout summary
     */
    CheckoutResponse prepareCheckout(String customerUuid, String guestSessionToken, CheckoutRequest request);

    /**
     * Get available payment methods for a cart.
     * Logic:
     *  1. Get cart by cartUuid → get product IDs from cart items
     *  2. Get payment method mappings for each product from product module
     *  3. Find the INTERSECTION of allowed payment methods across all products
     *  4. Validate each candidate payment method in the payment module
     *  5. Return only the payment methods common to ALL products
     *
     * @param cartUuid UUID of the cart
     * @param type     Optional filter by PaymentType (ONLINE, OFFLINE, POS)
     * @return List of PaymentMethodResponse (same response as existing getAllPaymentMethods)
     */
    List<PaymentMethodResponse> getPaymentMethodsForCart(String cartUuid, String type);
}