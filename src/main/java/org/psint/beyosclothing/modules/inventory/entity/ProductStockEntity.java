package org.psint.beyosclothing.modules.inventory.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Product Stock Entity
 * Database: beyos_inventory_db
 * Table: product_stock
 */
@Entity
@Table(name = "product_stock",
    indexes = {
        @Index(name = "idx_product_id", columnList = "product_id"),
        @Index(name = "idx_variant_id", columnList = "variant_id"),
        @Index(name = "idx_stock_quantity", columnList = "stock_quantity")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "unique_product_variant_stock", columnNames = {"product_id", "variant_id"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductStockEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", unique = true, nullable = false, length = 36)
    private String uuid;

    @Column(name = "product_id", nullable = false)
    private Long productId; // References products.id from product module

    @Column(name = "variant_id")
    private Long variantId; // References product_variants.id from product module

    @Column(name = "stock_quantity", nullable = false)
    private Integer stockQuantity = 0;

    @Column(name = "allow_backorder", nullable = false)
    private Boolean allowBackorder = false;

    @Column(name = "low_stock_threshold")
    private Integer lowStockThreshold;

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
}

