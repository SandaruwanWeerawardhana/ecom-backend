package org.psint.beyosclothing.modules.orders.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Order Status History Entity
 * Database: beyos_order
 * Table: order_status_history
 */
@Entity
@Table(name = "order_status_history", indexes = {
    @Index(name = "idx_order_id", columnList = "order_id"),
    @Index(name = "idx_new_status", columnList = "new_status"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderStatusHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "old_status", length = 50)
    private String oldStatus;

    @Column(name = "new_status", nullable = false, length = 50)
    private String newStatus;

    @Column(name = "changed_by")
    private Long changedBy;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

