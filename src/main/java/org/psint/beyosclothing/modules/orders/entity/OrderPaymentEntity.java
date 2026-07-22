package org.psint.beyosclothing.modules.orders.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Order Payment Entity
 * Database: beyos_order
 * Table: order_payments
 */
@Entity
@Table(name = "order_payments", indexes = {
    @Index(name = "idx_order_id", columnList = "order_id"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_gateway_transaction_id", columnList = "gateway_transaction_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderPaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "method", nullable = false, length = 50)
    private String method;

    @Column(name = "gateway_transaction_id", length = 100)
    private String gatewayTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum PaymentStatus {
        PENDING,
        SUCCESS,
        FAILED,
        REFUNDED
    }
}

