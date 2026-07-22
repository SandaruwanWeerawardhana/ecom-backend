package org.psint.beyosclothing.modules.payment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Payment Method Entity
 * Database: beyos_payment
 * Table: payment_methods
 */
@Entity
@Table(name = "payment_methods", indexes = {
        @Index(name = "idx_code", columnList = "code"),
        @Index(name = "idx_type", columnList = "type"),
        @Index(name = "idx_is_active", columnList = "is_active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentMethodEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", unique = true, nullable = false, length = 36)
    private String uuid;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "code", unique = true, nullable = false, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private PaymentType type;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "supports_refund")
    private Boolean supportsRefund = false;

    @Column(name = "supports_callback")
    private Boolean supportsCallback = false;

    @Column(name = "is_courier_fee_free")
    private Boolean isCourierFeeFree = false;

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

    public enum PaymentType {
        ONLINE,
        OFFLINE,
        POS
    }
}


