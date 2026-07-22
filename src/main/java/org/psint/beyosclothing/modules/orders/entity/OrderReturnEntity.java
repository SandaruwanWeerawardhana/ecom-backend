package org.psint.beyosclothing.modules.orders.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Order Return Entity
 * Database: beyos_order
 * Table: order_returns
 */
@Entity
@Table(name = "order_returns", indexes = {
    @Index(name = "idx_order_id", columnList = "order_id"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderReturnEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", unique = true, nullable = false, length = 36)
    private String uuid;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "order_item_id")
    private Long orderItemId;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ReturnStatus status = ReturnStatus.REQUESTED;

    @Column(name = "refund_amount", precision = 10, scale = 2)
    private BigDecimal refundAmount;

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

    public enum ReturnStatus {
        REQUESTED,
        APPROVED,
        REJECTED,
        REFUNDED
    }
}

