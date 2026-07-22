package org.psint.beyosclothing.modules.resellers.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Reseller Cart Entity
 * Represents a reseller's shopping cart
 */
@Entity
@Table(name = "reseller_carts", indexes = {
    @Index(name = "idx_reseller_carts_uuid", columnList = "uuid"),
    @Index(name = "idx_reseller_carts_reseller_id", columnList = "reseller_id"),
    @Index(name = "idx_reseller_carts_is_active", columnList = "is_active"),
    @Index(name = "idx_reseller_carts_reseller_active", columnList = "reseller_id, is_active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResellerCart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36)
    private String uuid;

    @Column(name = "reseller_id", nullable = false)
    private Long resellerId;

    @Column(precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "tax_amount", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "tax_percentage", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal taxPercentage = BigDecimal.ZERO;

    @Column(name = "discount_amount", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "date_created", nullable = false, updatable = false)
    private LocalDateTime dateCreated;

    @UpdateTimestamp
    @Column(name = "date_updated")
    private LocalDateTime dateUpdated;

    @PrePersist
    protected void onCreate() {
        if (uuid == null) {
            uuid = UUID.randomUUID().toString();
        }
    }

    // Helper methods
    public void calculateTotal() {
        BigDecimal calculatedTotal = subtotal != null ? subtotal : BigDecimal.ZERO;

        if (taxAmount != null) {
            calculatedTotal = calculatedTotal.add(taxAmount);
        }

        if (discountAmount != null) {
            calculatedTotal = calculatedTotal.subtract(discountAmount);
        }

        this.total = calculatedTotal.max(BigDecimal.ZERO);
    }

    public boolean isActive() {
        return Boolean.TRUE.equals(isActive);
    }

    public boolean hasItems() {
        // This would typically check if there are cart items
        // Implementation depends on whether you want bidirectional relationship
        return true;
    }
}

