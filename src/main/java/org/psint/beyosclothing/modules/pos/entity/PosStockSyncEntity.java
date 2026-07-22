package org.psint.beyosclothing.modules.pos.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * POS Stock Sync Entity
 * Database: beyos_pos
 * Table: pos_stock_sync
 * Audit trail for inventory deductions after POS order creation
 */
@Entity
@Table(name = "pos_stock_sync", indexes = {
        @Index(name = "idx_uuid", columnList = "uuid"),
        @Index(name = "idx_pos_order_id", columnList = "pos_order_id"),
        @Index(name = "idx_product_id", columnList = "product_id"),
        @Index(name = "idx_synced", columnList = "synced")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PosStockSyncEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, length = 36, unique = true)
    private String uuid;

    @Column(name = "pos_order_id", nullable = false)
    private Long posOrderId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "variant_id")
    private Long variantId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "synced", nullable = false)
    private Boolean synced = false;

    @Column(name = "synced_at")
    private LocalDateTime syncedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (uuid == null) {
            uuid = java.util.UUID.randomUUID().toString();
        }
        createdAt = LocalDateTime.now();
    }
}
