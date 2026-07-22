package org.psint.beyosclothing.modules.pos.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * POS Receipt Entity
 * Database: beyos_pos
 * Table: pos_receipts
 * Tracks printed receipt history for POS orders
 */
@Entity
@Table(name = "pos_receipts", indexes = {
        @Index(name = "idx_uuid", columnList = "uuid"),
        @Index(name = "idx_order_id", columnList = "order_id"),
        @Index(name = "idx_receipt_number", columnList = "receipt_number")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PosReceiptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", unique = true, nullable = false, length = 36)
    private String uuid;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "receipt_number", unique = true, nullable = false, length = 50)
    private String receiptNumber;

    @Column(name = "print_count", nullable = false)
    private Integer printCount = 1;

    @Column(name = "printed_at", nullable = false)
    private LocalDateTime printedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (uuid == null) {
            uuid = java.util.UUID.randomUUID().toString();
        }
        if (printedAt == null) {
            printedAt = LocalDateTime.now();
        }
        if (printCount == null) {
            printCount = 1;
        }
    }
}
