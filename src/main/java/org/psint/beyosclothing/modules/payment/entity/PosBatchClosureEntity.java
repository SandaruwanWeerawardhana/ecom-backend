package org.psint.beyosclothing.modules.payment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * POS Batch Closure Entity
 * Database: beyos_payment
 * Table: pos_batch_closures
 */
@Entity
@Table(name = "pos_batch_closures", indexes = {
    @Index(name = "idx_terminal_id", columnList = "terminal_id"),
    @Index(name = "idx_cashier_id", columnList = "cashier_id"),
    @Index(name = "idx_opened_at", columnList = "opened_at"),
    @Index(name = "idx_closed_at", columnList = "closed_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PosBatchClosureEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", unique = true, nullable = false, length = 36)
    private String uuid;

    @Column(name = "terminal_id", nullable = false, length = 50)
    private String terminalId;

    @Column(name = "cashier_id")
    private Long cashierId;

    @Column(name = "total_cash", precision = 10, scale = 2)
    private BigDecimal totalCash = BigDecimal.ZERO;

    @Column(name = "total_card", precision = 10, scale = 2)
    private BigDecimal totalCard = BigDecimal.ZERO;

    @Column(name = "total_orders")
    private Integer totalOrders = 0;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @PrePersist
    protected void onCreate() {
        if (uuid == null) {
            uuid = java.util.UUID.randomUUID().toString();
        }
    }
}

