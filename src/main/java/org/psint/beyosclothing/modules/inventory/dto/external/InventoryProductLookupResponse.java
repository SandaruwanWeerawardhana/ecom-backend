package org.psint.beyosclothing.modules.inventory.dto.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Response DTO returned from Product module to Inventory module via RabbitMQ.
 * Contains only the minimal fields that Inventory needs (title + SKU).
 * Queue: inventory.product.lookup.request  (request-reply pattern)
 * Producer: InventoryProductLookupConsumer (product module)
 * Consumer: InventoryProductLookupServiceImpl (inventory module)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryProductLookupResponse implements Serializable {

    /** Mirrors the requestId from the request for correlation. */
    private String requestId;

    /** Whether the product was found. */
    private Boolean found;

    /** Error description when found == false. */
    private String errorMessage;

    /** Product title (e.g., "Classic White T-Shirt"). */
    private String productTitle;

    private String productType;

    private Long categoryId;

    private String category;

    private String image;

    /**
     * Effective SKU – variant SKU when variantId was supplied,
     * otherwise the product-level SKU.
     */
    private String sku;

    /**
     * Inventory status from the variant (or product) – e.g. IN_STOCK, OUT_OF_STOCK, ON_BACKORDER.
     * Sourced from {@code product_variants.inventory_status}.
     */
    private String inventoryStatus;

    /**
     * Human readable attribute summary from the variant, e.g. "Size: M, Color: Black".
     * This mirrors {@code product_variants.attribute_summary} from the product DB.
     */
    private String attributeSummary;
}

