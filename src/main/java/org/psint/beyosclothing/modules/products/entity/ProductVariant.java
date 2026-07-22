package org.psint.beyosclothing.modules.products.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Product Variant Entity
 * Database: beyos_product_db
 * Table: product_variants
 */
@Entity
@Table(name = "product_variants", indexes = {
    @Index(name = "idx_product_id", columnList = "product_id"),
    @Index(name = "idx_sku", columnList = "sku")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", unique = true, nullable = false, length = 36)
    private String uuid;

    @Column(name = "sku", nullable = false, length = 100)
    private String sku;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "attribute_summary", length = 500)
    private String attributeSummary;// e.g., "Size: M, Color: Black"

    @Column(name = "weight_kg", precision = 10, scale = 3)
    private BigDecimal weightKg;

    @Column(name = "length_cm", precision = 10, scale = 2)
    private BigDecimal lengthCm;

    @Column(name = "width_cm", precision = 10, scale = 2)
    private BigDecimal widthCm;

    @Column(name = "height_cm", precision = 10, scale = 2)
    private BigDecimal heightCm;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "showcase_price", precision = 10, scale = 2)
    private BigDecimal showcasePrice;

    @Column(name = "sale_price", precision = 10, scale = 2)
    private BigDecimal salePrice;

    @Column(name = "sale_start")
    private LocalDateTime saleStart;

    @Column(name = "sale_end")
    private LocalDateTime saleEnd;

    @Column(name = "reseller_price", precision = 10, scale = 2)
    private BigDecimal resellerPrice;

    @Column(name = "wholesale_price", precision = 10, scale = 2)
    private BigDecimal wholesalePrice;

    @Column(name = "wholesale_min_qty")
    private Integer wholesaleMinQty;

    @Column(name = "production_cost", precision = 10, scale = 2)
    private BigDecimal productionCost;

    @Enumerated(EnumType.STRING)
    @Column(name = "inventory_status", nullable = false, length = 20)
    private InventoryStatus inventoryStatus = InventoryStatus.IN_STOCK;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "date_created", nullable = false, updatable = false)
    private LocalDateTime dateCreated;

    @Column(name = "date_updated", nullable = false)
    private LocalDateTime dateUpdated;

    @PrePersist
    protected void onCreate() {
        dateCreated = LocalDateTime.now();
        dateUpdated = LocalDateTime.now();
        if (uuid == null) {
            uuid = java.util.UUID.randomUUID().toString();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        dateUpdated = LocalDateTime.now();
    }

    public enum InventoryStatus {
        IN_STOCK, OUT_OF_STOCK, ON_BACKORDER
    }
}
