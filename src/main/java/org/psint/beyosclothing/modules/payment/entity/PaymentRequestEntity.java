package org.psint.beyosclothing.modules.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Payment Request Entity
 * Database: beyos_payment
 * Table: payment_requests
 */
@Entity
@Table(name = "payment_requests", indexes = {
        @Index(name = "idx_order_id", columnList = "order_id"),
        @Index(name = "idx_method_id", columnList = "method_id"),
        @Index(name = "idx_gateway_transaction_id", columnList = "gateway_transaction_id"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", unique = true, nullable = false, length = 36)
    private String uuid;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "method_id", nullable = false)
    private Long methodId;

    @Column(name = "gateway_transaction_id", length = 100)
    private String gatewayTransactionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_payload", columnDefinition = "JSON")
    private Map<String, Object> requestPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_payload", columnDefinition = "JSON")
    private Map<String, Object> responsePayload;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", length = 10)
    private String currency = "LKR";

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private PaymentRequestStatus status = PaymentRequestStatus.PENDING;

    @Column(name = "redirect_url", length = 500)
    private String redirectUrl;

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

    public enum PaymentRequestStatus {
        PENDING,
        REDIRECTED,
        PAID,
        FAILED,
        CANCELLED
    }
}


