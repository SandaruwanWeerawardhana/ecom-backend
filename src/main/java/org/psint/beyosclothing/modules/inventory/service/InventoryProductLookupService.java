package org.psint.beyosclothing.modules.inventory.service;

import org.psint.beyosclothing.modules.inventory.dto.external.InventoryProductLookupRequest;
import org.psint.beyosclothing.modules.inventory.dto.external.InventoryProductLookupResponse;

import java.util.List;
import java.util.Map;

/**
 * Service used by the Inventory module to look up product information
 * from the Product module via RabbitMQ (request-reply pattern).
 *
 * This interface deliberately avoids any direct import of Product-module
 * classes so that the two modules remain loosely coupled.
 */
public interface InventoryProductLookupService {

    /**
     * Look up the title and effective SKU for the given product / variant.
     *
     * @param productId ID of the product
     * @param variantId ID of the variant, or {@code null} for SIMPLE products
     * @return lookup response (never {@code null}; check {@code found} flag)
     */
    InventoryProductLookupResponse lookupProductInfo(Long productId, Long variantId);

    Map<String, InventoryProductLookupResponse> lookupProductInfoBulk(
            List<InventoryProductLookupRequest.LookupItem> items);
}

