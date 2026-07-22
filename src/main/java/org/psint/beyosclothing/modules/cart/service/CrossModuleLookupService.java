package org.psint.beyosclothing.modules.cart.service;

import org.psint.beyosclothing.modules.cart.dto.external.*;
import org.psint.beyosclothing.shared.dto.CustomerLookupResponse;

/**
 * Cross-Module Communication Service
 * Handles communication with other modules via RabbitMQ
 */
public interface CrossModuleLookupService {

    /**
     * Lookup customer by UUID from Customer module
     * @param customerUuid Customer UUID
     * @return Customer details or null if not found
     */
    CustomerLookupResponse lookupCustomerByUuid(String customerUuid);

    /**
     * Lookup product by UUID from Product module
     * @param productUuid Product UUID
     * @return Product details or null if not found
     */
    ProductLookupResponse lookupProductByUuid(String productUuid);

    /**
     * Lookup product variant by UUID from Product module
     * @param productUuid Product UUID
     * @param variantUuid Variant UUID
     * @return Product and variant details or null if not found
     */
    ProductLookupResponse lookupProductVariantByUuid(String productUuid, String variantUuid);

    /**
     * Check stock availability from Inventory module
     * @param productId Product ID
     * @param variantId Variant ID
     * @param quantity Desired quantity
     * @return Stock availability response
     */
    StockCheckResponse checkStockAvailability(Long productId, Long variantId, Integer quantity);

    /**
     * Validate promo code from Promotion module
     * @param request Promo code validation request
     * @return Promo code validation response
     */
    PromoValidationResponse validatePromoCode(PromoValidationRequest request);

    /**
     * Lookup product details by ID (for cart item display)
     * @param productId Product ID
     * @param variantId Variant ID (optional)
     * @return Product details or null if not found
     */
    ProductDetailsLookupResponse lookupProductDetailsById(Long productId, Long variantId);
}