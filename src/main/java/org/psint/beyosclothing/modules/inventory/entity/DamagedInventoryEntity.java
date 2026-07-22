package org.psint.beyosclothing.modules.inventory.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Damaged Inventory Entity
 * Database: beyos_inventory_db
 * Table: damaged_inventory
 */
@Entity
@Table(name = "damaged_inventory",
    indexes = {
        @Index(name = "idx_product_id", columnList = "product_id"),
        @Index(name = "idx_variant_id", columnList = "variant_id"),
        @Index(name = "idx_source", columnList = "source"),
        @Index(name = "idx_inspected_by", columnList = "inspected_by"),
        @Index(name = "idx_date_created", columnList = "date_created")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DamagedInventoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", unique = true, nullable = false, length = 36)
    private String uuid;

    @Column(name = "product_id", nullable = false)
    private Long productId; // References products.id from product module

    @Column(name = "variant_id")
    private Long variantId; // References product_variants.id from product module

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private DamageSource source;

    @Column(name = "inspected_by")
    private Long inspectedBy; // Staff/admin who marked it damaged

    @Column(name = "reference_return_id")
    private Long referenceReturnId; // If from return, link to return record

    @Column(name = "reference_order_id")
    private Long referenceOrderId; // If related to order, link to order

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

    public enum DamageSource {
        CUSTOMER_RETURN,   // Damaged item returned by customer
        WAREHOUSE_DAMAGE,  // Damaged in warehouse/storage
        POS_DAMAGE         // Damaged at point of sale
    }
}

