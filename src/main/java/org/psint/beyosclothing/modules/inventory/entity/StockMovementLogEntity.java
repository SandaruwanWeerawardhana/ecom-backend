package org.psint.beyosclothing.modules.inventory.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Stock Movement Logs Entity
 * Database: beyos_inventory_db
 * Table: stock_movement_logs
 */
@Entity
@Table(name = "stock_movement_logs", indexes = {
    @Index(name = "idx_product_id", columnList = "product_id"),
    @Index(name = "idx_variant_id", columnList = "variant_id"),
    @Index(name = "idx_movement_type", columnList = "movement_type"),
    @Index(name = "idx_reference_type", columnList = "reference_type"),
    @Index(name = "idx_date_created", columnList = "date_created")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockMovementLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", unique = true, nullable = false, length = 36)
    private String uuid;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "variant_id")
    private Long variantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 20)
    private MovementType movementType;

    @Column(name = "quantity_changed", nullable = false)
    private Integer quantityChanged;

    @Column(name = "quantity_before", nullable = false)
    private Integer quantityBefore;

    @Column(name = "quantity_after", nullable = false)
    private Integer quantityAfter;

    @Column(name = "reference_type", length = 100)
    private String referenceType; // ORDER, PURCHASE, ADJUSTMENT, etc.

    @Column(name = "reference_id")
    private Long referenceId; // ID of the reference entity

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "performed_by", length = 100)
    private String performedBy;

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

    public enum MovementType {
        IN, OUT, ADJUSTMENT, RETURN
    }
}

