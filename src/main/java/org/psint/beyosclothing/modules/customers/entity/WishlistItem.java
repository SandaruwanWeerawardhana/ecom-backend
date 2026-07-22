package org.psint.beyosclothing.modules.customers.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Wishlist Item Entity
 * Table: wishlist_items in beyos_customers_db
 * Stores products added to customer wishlists
 */
@Entity
@Table(name = "wishlist_items",
    indexes = {
        @Index(name = "idx_customer_id", columnList = "customer_id"),
        @Index(name = "idx_product_id", columnList = "product_id"),
        @Index(name = "idx_is_available", columnList = "is_available")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_customer_product", columnNames = {"customer_id", "product_id"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WishlistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", unique = true, nullable = false, length = 36)
    private String uuid;

    @Column(name = "customer_id", nullable = false)
    private Long customerId; // Reference to Customer table

    @Column(name = "product_id", nullable = false)
    private Long productId; // Reference to Product table (product module)

    @Column(name = "variant_id")
    private Long variantId; // Optional: specific variant

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", length = 20)
    private Priority priority;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "tags", columnDefinition = "JSON")
    private String tags; // JSON array for custom tags

    @Column(name = "price_snapshot", precision = 25, scale = 6)
    private BigDecimal priceSnapshot; // Price when added to wishlist

    @Column(name = "sale_price_snapshot", precision = 25, scale = 6)
    private BigDecimal salePriceSnapshot; // Sale price when added

    @Column(name = "captured_at", nullable = false)
    private LocalDateTime capturedAt;

    @Column(name = "is_available", nullable = false)
    @Builder.Default
    private Boolean isAvailable = true;

    @Column(name = "source", length = 50)
    private String source; // Source: WEB, MOBILE_APP, etc.

    @Column(name = "date_created", nullable = false, updatable = false)
    private LocalDateTime dateCreated;

    @Column(name = "date_updated", nullable = false)
    private LocalDateTime dateUpdated;

    @PrePersist
    protected void onCreate() {
        dateCreated = LocalDateTime.now();
        dateUpdated = LocalDateTime.now();
        capturedAt = LocalDateTime.now();
        if (uuid == null) {
            uuid = java.util.UUID.randomUUID().toString();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        dateUpdated = LocalDateTime.now();
    }

    public enum Priority {
        LOW, MEDIUM, HIGH
    }
}

