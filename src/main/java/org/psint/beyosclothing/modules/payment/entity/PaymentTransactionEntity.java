package org.psint.beyosclothing.modules.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Payment Transaction Entity
 * Database: beyos_payment
 * Table: payment_transactions
 */
@Entity
@Table(name = "payment_transactions", indexes = {
    @Index(name = "idx_order_id", columnList = "order_id"),
    @Index(name = "idx_payment_request_id", columnList = "payment_request_id"),
    @Index(name = "idx_gateway_transaction_id", columnList = "gateway_transaction_id"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_paid_at", columnList = "paid_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", unique = true, nullable = false, length = 36)
    private String uuid;

    @Column(name = "payment_request_id", nullable = true)
    private Long paymentRequestId;

    @Column(name = "method_id", nullable = true)
    private Long methodId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", length = 10)
    private String currency = "LKR";

    @Column(name = "gateway_transaction_id", length = 100)
    private String gatewayTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private TransactionStatus status = TransactionStatus.PENDING;

    @Column(name = "status_message")
    private String statusMessage;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

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

    public enum TransactionStatus {
        SUCCESS,
        FAILED,
        PENDING,
        REFUNDED
    }
}
