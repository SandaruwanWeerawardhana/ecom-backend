package org.psint.beyosclothing.modules.customers.entity;

import jakarta.persistence.*;
import lombok.*;
import org.psint.beyosclothing.core.audit.BaseEntity;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

/**
 * Customer Review Entity
 * Table: customer_reviews in beyos_customers_db
 * Stores product reviews submitted by customers
 */
@Entity
@Table(name = "customer_reviews", indexes = {
    @Index(name = "idx_customer_id", columnList = "customer_id"),
    @Index(name = "idx_product_id", columnList = "product_id"),
    @Index(name = "idx_is_approved", columnList = "is_approved"),
    @Index(name = "idx_rating", columnList = "rating")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerReview extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", unique = true, nullable = false, length = 36)
    private String uuid;

    @Column(name = "customer_id", nullable = false)
    private Long customerId; // Reference to Customer table

    @Column(name = "product_id", nullable = false)
    private Long productId; // Reference to Product table (product module)

    @Column(name = "rating", nullable = false, precision = 2, scale = 1)
    private BigDecimal rating; // 1.0 to 5.0

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "is_approved", nullable = false)
    @Builder.Default
    private Boolean isApproved = false;

    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<ReviewImage> images = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        if (uuid == null) {
            uuid = java.util.UUID.randomUUID().toString();
        }
    }
}

