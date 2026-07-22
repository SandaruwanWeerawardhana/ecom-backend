package org.psint.beyosclothing.modules.pos.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * POS Cart Entity
 * Database: beyos_pos
 * Table: pos_carts
 * Represents active POS transactions before checkout
 */
@Entity
@Table(name = "pos_carts", indexes = {
        @Index(name = "idx_uuid", columnList = "uuid"),
        @Index(name = "idx_terminal_id", columnList = "terminal_id"),
        @Index(name = "idx_cashier_id", columnList = "cashier_id"),
        @Index(name = "idx_customer_id", columnList = "customer_id"),
        @Index(name = "idx_is_active", columnList = "is_active"),
        @Index(name = "idx_pos_carts_completed_covering", columnList = "is_draft, is_active, created_at, subtotal")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PosCartEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", unique = true, nullable = false, length = 36)
    private String uuid;

    @Column(name = "terminal_id", nullable = false)
    private Long terminalId;

    @Column(name = "cashier_id", nullable = false)
    private Long cashierId;

    @Column(name = "customer_id")
    private Long customerId; // NULL for walk-in customers

    @Column(name = "customer_type", length = 8)
    private String customerType; // POS | ONLINE | WALK_IN

    @Column(name = "subtotal", nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "tax_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxPercentage = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "total", nullable = false, precision = 10, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "is_draft", nullable = false)
    private Boolean isDraft = false;

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
        // Initialize BigDecimal fields if null
        if (subtotal == null) subtotal = BigDecimal.ZERO;
        if (taxAmount == null) taxAmount = BigDecimal.ZERO;
        if (taxPercentage == null) taxPercentage = BigDecimal.ZERO;
        if (discountAmount == null) discountAmount = BigDecimal.ZERO;
        if (total == null) total = BigDecimal.ZERO;
        if (isActive == null) isActive = true;
        if (isDraft == null) isDraft = false;
        if (customerType == null) customerType = "WALK_IN";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Calculate total: subtotal + taxAmount - discountAmount
     */
    public void calculateTotal() {
        this.total = this.subtotal
                .add(this.taxAmount)
                .subtract(this.discountAmount);
    }

    /**
     * Calculate tax amount: subtotal × (taxPercentage / 100)
     */
    public void calculateTaxAmount() {
        if (this.taxPercentage != null && this.subtotal != null) {
            this.taxAmount = this.subtotal
                    .multiply(this.taxPercentage)
                    .divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
        }
    }

    /**
     * Check if this is a walk-in customer (no customer ID)
     */
    public boolean isWalkInCustomer() {
        return customerId == null;
    }
}
