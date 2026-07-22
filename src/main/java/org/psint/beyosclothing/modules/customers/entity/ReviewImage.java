package org.psint.beyosclothing.modules.customers.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Review Image Entity
 * Table: review_images in beyos_customers_db
 * Stores images attached to customer reviews
 */
@Entity
@Table(name = "review_images", indexes = {
    @Index(name = "idx_review_id", columnList = "review_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", unique = true, nullable = false, length = 36)
    private String uuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", nullable = false)
    private CustomerReview review;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(name = "date_created", nullable = false, updatable = false)
    private LocalDateTime dateCreated;

    @PrePersist
    protected void onCreate() {
        dateCreated = LocalDateTime.now();
        if (uuid == null) {
            uuid = java.util.UUID.randomUUID().toString();
        }
    }
}

