package org.psint.beyosclothing.modules.payment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Payment Method Config Entity
 * Database: beyos_payment
 * Table: payment_method_config
 */
@Entity
@Table(name = "payment_method_config", indexes = {
        @Index(name = "idx_method_id", columnList = "method_id"),
        @Index(name = "idx_config_key", columnList = "config_key")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentMethodConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "method_id", nullable = false)
    private Long methodId;

    @Column(name = "label", nullable = false, length = 100)
    private String label;

    @Column(name = "config_key", nullable = false, length = 100)
    private String configKey;

    @Column(name = "config_value", nullable = false, columnDefinition = "TEXT")
    private String configValue;

    @Column(name = "is_secret")
    private Boolean isSecret = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}



