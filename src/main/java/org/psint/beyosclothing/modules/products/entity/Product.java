package org.psint.beyosclothing.modules.products.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Product Entity - Main product table
 * Database: beyos_product_db
 * Table: products
 */
@Entity
@Table(name = "products", indexes = {
        @Index(name = "idx_category_id", columnList = "category_id"),
        @Index(name = "idx_product_type", columnList = "product_type"),
        @Index(name = "idx_visibility", columnList = "visibility"),
        @Index(name = "idx_featured", columnList = "featured"),
        @Index(name = "idx_is_publish", columnList = "is_publish")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", unique = true, nullable = false, length = 36)
    private String uuid;

    @Column(name = "sku", nullable = false, length = 100)
    private String sku;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "slug", nullable = false, length = 500)
    private String slug;

    @Column(name = "short_description", columnDefinition = "TEXT")
    private String shortDescription;

    @Column(name = "description", columnDefinition = "LONGTEXT")
    private String description;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "default_variant_id")
    private Long defaultVariantId;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "showcase_price", precision = 10, scale = 2)
    private BigDecimal showcasePrice;

    @Column(name = "sale_price", precision = 10, scale = 2)
    private BigDecimal salePrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 20)
    private ProductType productType = ProductType.SIMPLE;

    @Column(name = "sale_start")
    private LocalDateTime saleStart;

    @Column(name = "sale_end")
    private LocalDateTime saleEnd;

    @Column(name = "featured", nullable = false)
    private Boolean featured = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    private ProductVisibility visibility = ProductVisibility.PUBLIC;

    @Column(name = "sold_individually", nullable = false)
    private Boolean soldIndividually = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_mode", nullable = false, length = 20)
    private PricingMode pricingMode = PricingMode.PRODUCT_LEVEL;

    @Column(name = "wholesale_price", precision = 10, scale = 2)
    private BigDecimal wholesalePrice;

    @Column(name = "wholesale_min_qty")
    private Integer wholesaleMinQty;

    @Column(name = "production_cost", precision = 10, scale = 2)
    private BigDecimal productionCost;

    @Column(name = "initial_stock_quantity")
    private Integer initialStockQuantity = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "inventory_status", nullable = false, length = 20)
    private InventoryStatus inventoryStatus = InventoryStatus.IN_STOCK;

    @Column(name = "is_publish", nullable = false)
    private Boolean isPublish = false;

    @Column(name = "is_reseller_product", nullable = false)
    private Boolean isResellerProduct = false;

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

    // Enums
    public enum ProductType {
        SIMPLE, VARIABLE
    }

    public enum ProductVisibility {
        PUBLIC, PRIVATE, HIDDEN
    }

    public enum PricingMode {
        PRODUCT_LEVEL, VARIANT_LEVEL
    }

    public enum InventoryStatus {
        IN_STOCK, OUT_OF_STOCK, ON_BACKORDER
    }
}