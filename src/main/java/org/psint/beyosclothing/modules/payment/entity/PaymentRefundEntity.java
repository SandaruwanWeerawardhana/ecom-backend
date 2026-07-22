package org.psint.beyosclothing.modules.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Payment Refund Entity
 * Database: beyos_payment
 * Table: payment_refunds
 */
@Entity
@Table(name = "payment_refunds", indexes = {
    @Index(name = "idx_order_id", columnList = "order_id"),
    @Index(name = "idx_transaction_id", columnList = "transaction_id"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRefundEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", unique = true, nullable = false, length = 36)
    private String uuid;

    @Column(name = "transaction_id", nullable = false)
    private Long transactionId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "reason")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private RefundStatus status = RefundStatus.PENDING;

    @Column(name = "gateway_reference", length = 100)
    private String gatewayReference;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "refund_payload", columnDefinition = "JSON")
    private Map<String, Object> refundPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "callback_payload", columnDefinition = "JSON")
    private Map<String, Object> callbackPayload;

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

    public enum RefundStatus {
        PENDING,
        PROCESSING,
        SUCCESS,
        FAILED
    }
}

