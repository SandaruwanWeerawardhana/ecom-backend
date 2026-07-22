package org.psint.beyosclothing.modules.inventory.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * POS Stock Sync Logs Entity
 * Database: beyos_inventory_db
 * Table: pos_stock_sync_logs
 */
@Entity
@Table(name = "pos_stock_sync_logs",
    indexes = {
        @Index(name = "idx_product_id", columnList = "product_id"),
        @Index(name = "idx_variant_id", columnList = "variant_id"),
        @Index(name = "idx_sync_status", columnList = "sync_status"),
        @Index(name = "idx_pos_terminal", columnList = "pos_terminal"),
        @Index(name = "idx_date_created", columnList = "date_created")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PosStockSyncLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", unique = true, nullable = false, length = 36)
    private String uuid;

    @Column(name = "product_id", nullable = false)
    private Long productId; // References products.id from product module

    @Column(name = "variant_id")
    private Long variantId; // References product_variants.id from product module

    @Column(name = "pos_change", nullable = false)
    private Integer posChange; // Stock change amount: -3 sold, +10 restocked, etc.

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false, length = 10)
    private SyncStatus syncStatus = SyncStatus.SUCCESS;

    @Column(name = "pos_terminal", length = 50)
    private String posTerminal; // Which POS device created the change

    @Column(name = "reference_id", length = 100)
    private String referenceId; // Invoice ID or restock ID

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

    public enum SyncStatus {
        SUCCESS,  // Stock sync completed successfully
        FAILED    // Stock sync failed
    }
}

