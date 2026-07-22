package org.psint.beyosclothing.modules.delivery.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Table(name = "courier_rates", indexes = {
        @Index(name = "idx_courier_rate_uuid", columnList = "uuid", unique = true),
        @Index(name = "idx_courier_rate_customer_type", columnList = "customer_type"),
        @Index(name = "idx_courier_rate_payment_method", columnList = "payment_method_id"),
        @Index(name = "idx_courier_rate_effective_from", columnList = "effective_from")
})
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourierRate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", unique = true, nullable = false, length = 36)
    private String uuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "courier_id", nullable = false, foreignKey = @ForeignKey(name = "fk_courier_rate_courier"))
    private Courier courier;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type", nullable = false)
    private CustomerType customerType;

    @Column(name = "payment_method_id", nullable = true)
    private Long paymentMethodId;

    @Column(name = "first_kg_price", precision = 10, scale = 2)
    private BigDecimal firstKgPrice;

    @Column(name = "additional_kg_price", precision = 10, scale = 2)
    private BigDecimal additionalKgPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "weight_granularity", nullable = false)
    private WeightGranularity weightGranularity = WeightGranularity.PER_KG;

    @Column(name = "min_charge", precision = 10, scale = 2)
    private BigDecimal minCharge;

    @Column(name = "max_charge", precision = 10, scale = 2)
    private BigDecimal maxCharge;

    @Column(name = "effective_to")
    private LocalDateTime effectiveTo;

    @Column(name = "effective_from")
    private LocalDateTime effectiveFrom;

    @Column(name = "date_created", nullable = false, updatable = false)
    private LocalDateTime dateCreated;

    @Column(name = "date_updated")
    private LocalDateTime dateUpdated;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @PrePersist
    protected void onCreate() {
        dateCreated = LocalDateTime.now();
        dateUpdated = LocalDateTime.now();
        if (isActive == null) {
            isActive = true;
        }
        if (uuid == null) {
            uuid = UUID.randomUUID().toString();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        dateUpdated = LocalDateTime.now();
    }

    public enum WeightGranularity {
        PER_KG,
        PER_0_5KG,
        PER_0_1KG
    }

    public enum CustomerType {
        CUSTOMER,
        RESELLER,
        BOTH
    }
}
