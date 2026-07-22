package org.psint.beyosclothing.modules.payment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Entity
@Table(name = "payment_method_fees", indexes = {@Index(name = "idx_method_customer", columnList = "method_id,customer_type")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentMethodFeeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", unique = true, nullable = false, length = 36)
    private String uuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "method_id", foreignKey = @ForeignKey(name = "fk_fee_payment_method"))
    private PaymentMethodEntity paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type", nullable = false)
    private CustomerType customerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_type")
    private FeeType feeType = FeeType.FIXED;

    @Column(name = "fee_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal feeValue;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "is_free_shipping")
    private Boolean isFreeShipping = false;

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

    public enum CustomerType {
        CUSTOMER, RESELLER, BOTH
    }

    public enum FeeType {
        FIXED, PERCENTAGE
    }
}

