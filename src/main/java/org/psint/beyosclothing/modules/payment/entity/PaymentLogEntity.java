package org.psint.beyosclothing.modules.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Payment Log Entity
 * Database: beyos_payment
 * Table: payment_logs
 */
@Entity
@Table(name = "payment_logs", indexes = {
    @Index(name = "idx_method_id", columnList = "method_id"),
    @Index(name = "idx_payment_request_id", columnList = "payment_request_id"),
    @Index(name = "idx_transaction_id", columnList = "transaction_id"),
    @Index(name = "idx_type", columnList = "type"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "method_id")
    private Long methodId;

    @Column(name = "payment_request_id")
    private Long paymentRequestId;

    @Column(name = "transaction_id")
    private Long transactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private LogType type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "JSON")
    private Map<String, Object> payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum LogType {
        REQUEST,
        RESPONSE,
        CALLBACK
    }
}

