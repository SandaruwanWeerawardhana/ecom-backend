package org.psint.beyosclothing.modules.payment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * POS Payment Entity
 * Database: beyos_payment
 * Table: pos_payments
 */
@Entity
@Table(name = "pos_payments", indexes = {
    @Index(name = "idx_order_id", columnList = "order_id"),
    @Index(name = "idx_method_id", columnList = "method_id"),
    @Index(name = "idx_pos_terminal", columnList = "pos_terminal"),
    @Index(name = "idx_cashier_id", columnList = "cashier_id"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PosPaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", unique = true, nullable = false, length = 36)
    private String uuid;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "method_id", nullable = false)
    private Long methodId;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "pos_terminal", length = 100)
    private String posTerminal;

    @Column(name = "cashier_id")
    private Long cashierId;

    @Column(name = "card_last4", length = 10)
    private String cardLast4;

    @Column(name = "card_type", length = 50)
    private String cardType;

    @Column(name = "approval_code", length = 50)
    private String approvalCode;

    @Column(name = "reference_id", length = 100)
    private String referenceId;

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
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

