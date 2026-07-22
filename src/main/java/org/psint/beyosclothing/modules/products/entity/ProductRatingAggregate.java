package org.psint.beyosclothing.modules.products.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Product Rating Aggregates Entity
 * Database: beyos_product_db
 * Table: product_rating_aggregates
 */
@Entity
@Table(name = "product_rating_aggregates", indexes = {
    @Index(name = "idx_product_id", columnList = "product_id"),
    @Index(name = "idx_rating_average", columnList = "rating_average")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductRatingAggregate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", unique = true, nullable = false, length = 36)
    private String uuid;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "review_count", nullable = false)
    private Integer reviewCount = 0;

    @Column(name = "rating_total", precision = 10, scale = 2, nullable = false)
    private BigDecimal ratingTotal = BigDecimal.ZERO;

    @Column(name = "rating_average", precision = 3, scale = 2, nullable = false)
    private BigDecimal ratingAverage = BigDecimal.ZERO;

    @Column(name = "rating_1_count", nullable = false)
    private Integer rating1Count = 0;

    @Column(name = "rating_2_count", nullable = false)
    private Integer rating2Count = 0;

    @Column(name = "rating_3_count", nullable = false)
    private Integer rating3Count = 0;

    @Column(name = "rating_4_count", nullable = false)
    private Integer rating4Count = 0;

    @Column(name = "rating_5_count", nullable = false)
    private Integer rating5Count = 0;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    @Column(name = "date_created", nullable = false, updatable = false)
    private LocalDateTime dateCreated;

    @Column(name = "date_updated", nullable = false)
    private LocalDateTime dateUpdated;

    @PrePersist
    protected void onCreate() {
        dateCreated = LocalDateTime.now();
        dateUpdated = LocalDateTime.now();
        if (uuid == null) {
            uuid = java.util.UUID.randomUUID().toString();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        dateUpdated = LocalDateTime.now();
        lastUpdated = LocalDateTime.now();
    }
}
