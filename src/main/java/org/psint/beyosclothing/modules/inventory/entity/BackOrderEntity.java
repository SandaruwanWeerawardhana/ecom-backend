package org.psint.beyosclothing.modules.inventory.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Backorders Entity
 * Database: beyos_inventory_db
 * Table: backorders
 */
@Entity
@Table(name = "backorders", indexes = {
    @Index(name = "idx_product_id", columnList = "product_id"),
    @Index(name = "idx_variant_id", columnList = "variant_id"),
    @Index(name = "idx_order_id", columnList = "order_id"),
    @Index(name = "idx_fulfilled", columnList = "fulfilled")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BackOrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", unique = true, nullable = false, length = 36)
    private String uuid;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "variant_id")
    private Long variantId;

    @Column(name = "order_id", nullable = false)
    private Long orderId; // References orders table from order module

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "fulfilled", nullable = false)
    private Boolean fulfilled = false;

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

