package org.psint.beyosclothing.modules.products.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Variant Created Event
 * Published when a new product variant is created
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VariantCreatedEvent implements Serializable {
    private Long variantId;
    private String uuid;
    private Long productId;
    private String sku;
    private String attributeSummary;

    // Stock/Inventory fields
    private Integer initialStockQuantity;
    private Boolean allowBackorder;
    private Integer lowStockThreshold;

    private String createdBy;
    private LocalDateTime createdAt;
}

