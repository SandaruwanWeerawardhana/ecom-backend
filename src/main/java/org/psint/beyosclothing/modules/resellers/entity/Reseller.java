package org.psint.beyosclothing.modules.resellers.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Reseller Entity
 * Represents a reseller in the system
 */
@Entity
@Table(name = "resellers", indexes = {
    @Index(name = "idx_resellers_uuid", columnList = "uuid"),
    @Index(name = "idx_resellers_user_id", columnList = "user_id"),
    @Index(name = "idx_resellers_email", columnList = "email"),
    @Index(name = "idx_resellers_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reseller {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36)
    private String uuid;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(nullable = false)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(name = "image_url", length = 2500)
    private String imageUrl;

    @Column(name = "address_line1")
    private String addressLine1;

    @Column(name = "address_line2")
    private String addressLine2;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String district;

    @Column(length = 100)
    private String province;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ResellerStatus status = ResellerStatus.PENDING;

    @Column(name = "allow_price_override")
    @Builder.Default
    private Boolean allowPriceOverride = false;

    @Column(name = "min_allowed_markup_pct", precision = 5, scale = 2)
    private BigDecimal minAllowedMarkupPct;

    @Column(name = "max_allowed_markup_pct", precision = 5, scale = 2)
    private BigDecimal maxAllowedMarkupPct;

    @Column(name = "credit_balance", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal creditBalance = BigDecimal.ZERO;

    @Column(name = "credit_limit", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal creditLimit = BigDecimal.ZERO;

    @CreationTimestamp
    @Column(name = "date_created", nullable = false, updatable = false)
    private LocalDateTime dateCreated;

    @UpdateTimestamp
    @Column(name = "date_updated")
    private LocalDateTime dateUpdated;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @PrePersist
    protected void onCreate() {
        if (uuid == null) {
            uuid = UUID.randomUUID().toString();
        }
    }

    // Helper methods
    public boolean isApproved() {
        return status == ResellerStatus.APPROVED;
    }

    public boolean isPending() {
        return status == ResellerStatus.PENDING;
    }

    public boolean canPlaceOrders() {
        return isApproved() && isActive;
    }

    public BigDecimal getAvailableCredit() {
        if (creditLimit == null || creditBalance == null) {
            return BigDecimal.ZERO;
        }
        return creditLimit.subtract(creditBalance).max(BigDecimal.ZERO);
    }

    public String getFullName() {
        if (firstName == null && lastName == null) {
            return email;
        }
        return (firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "");
    }
}

