package org.psint.beyosclothing.modules.payment.service;

import org.psint.beyosclothing.modules.payment.dto.external.*;

/**
 * Cross-Module Communication Service for Payment Module
 * Handles communication with other modules via RabbitMQ
 */
public interface PaymentCrossModuleLookupService {

    /**
     * Lookup customer delivery address from Customer module
     * @param addressId Address ID to lookup
     * @return Customer address details or null if not found
     */
    CustomerAddressLookupResponse lookupCustomerAddress(Long addressId);

    /**
     * Get cart items with product details from Cart module
     * @param customerUuid Customer UUID (null for guest)
     * @param guestSessionToken Guest session token (null for customer)
     * @param selectedItemUuids List of selected item UUIDs (optional)
     * @return Cart items with details
     */
    CartItemsLookupResponse getCartItemsForCheckout(String customerUuid, String guestSessionToken, java.util.List<String> selectedItemUuids);

    /**
     * Calculate shipping cost from Delivery module
     * @param request Shipping calculation request
     * @return Shipping cost calculation result
     */
    ShippingCalculationResponse calculateShippingCost(ShippingCalculationRequest request);
}
