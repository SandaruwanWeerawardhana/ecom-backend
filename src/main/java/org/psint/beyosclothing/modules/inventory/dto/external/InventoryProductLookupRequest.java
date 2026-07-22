package org.psint.beyosclothing.modules.inventory.dto.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * Request DTO sent from Inventory module to Product module via RabbitMQ.
 * Queue: inventory.product.lookup.request
 * Producer: InventoryProductLookupServiceImpl (inventory module)
 * Consumer: InventoryProductLookupConsumer  (product module)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryProductLookupRequest implements Serializable {

    /** Correlation ID so the caller can match the reply. */
    private String requestId;

    /** ID of the product to look up. */
    private Long productId;

    /** ID of the variant to look up (optional – null for SIMPLE products). */
    private Long variantId;

    private List<LookupItem> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LookupItem implements Serializable {

        private Long productId;

        private Long variantId;
    }
}

