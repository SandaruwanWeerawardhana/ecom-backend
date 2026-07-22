package org.psint.beyosclothing.modules.promotions.service;

import org.psint.beyosclothing.modules.cart.dto.external.ProductDetailsLookupResponse;

/**
 * Cross-Module Lookup Service Interface for Promotions Module
 * Handles lookups to other modules (Product, etc.)
 */
public interface CrossModuleLookupService {

    /**
     * Lookup product details by ID
     * Used for getting product prices for FREE_PRODUCT promotions
     *
     * @param productId Product ID
     * @param variantId Variant ID (optional)
     * @return Product details including price
     */
    ProductDetailsLookupResponse lookupProductDetailsById(Long productId, Long variantId);
}

