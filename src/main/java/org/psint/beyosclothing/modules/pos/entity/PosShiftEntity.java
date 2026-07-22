package org.psint.beyosclothing.modules.pos.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * POS Shift Entity
 * Database: beyos_pos
 * Table: pos_shifts
 * Represents cashier shift tracking and cash drawer reconciliation
 */
@Entity
@Table(name = "pos_shifts", indexes = {
        @Index(name = "idx_uuid", columnList = "uuid"),
        @Index(name = "idx_cashier_id", columnList = "cashier_id"),
        @Index(name = "idx_terminal_id", columnList = "terminal_id"),
        @Index(name = "idx_opened_at", columnList = "opened_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PosShiftEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", unique = true, nullable = false, length = 36)
    private String uuid;

    @Column(name = "cashier_id", nullable = false)
    private Long cashierId;

    @Column(name = "terminal_id", nullable = false)
    private Long terminalId;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt; // NULL = active shift

    @Column(name = "opening_balance", nullable = false, precision = 10, scale = 2)
    private BigDecimal openingBalance = BigDecimal.ZERO;

    @Column(name = "closing_balance", precision = 10, scale = 2)
    private BigDecimal closingBalance;

    @Column(name = "expected_balance", nullable = false, precision = 10, scale = 2)
    private BigDecimal expectedBalance = BigDecimal.ZERO;

    @Column(name = "difference", nullable = false, precision = 10, scale = 2)
    private BigDecimal difference = BigDecimal.ZERO;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (uuid == null) {
            uuid = java.util.UUID.randomUUID().toString();
        }
        if (openingBalance == null) openingBalance = BigDecimal.ZERO;
        if (expectedBalance == null) expectedBalance = BigDecimal.ZERO;
        if (difference == null) difference = BigDecimal.ZERO;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Check if shift is currently active (not closed)
     */
    public boolean isActive() {
        return closedAt == null;
    }

    /**
     * Calculate difference: closingBalance - expectedBalance
     */
    public void calculateDifference() {
        if (closingBalance != null && expectedBalance != null) {
            this.difference = closingBalance.subtract(expectedBalance);
        }
    }
}

