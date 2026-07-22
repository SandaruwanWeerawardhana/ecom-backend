package org.psint.beyosclothing.modules.products.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Product Meta Entity (SEO)
 * Database: beyos_product_db
 * Table: product_meta
 */
@Entity
@Table(name = "product_meta", indexes = {
    @Index(name = "idx_product_id", columnList = "product_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductMeta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", unique = true, nullable = false, length = 36)
    private String uuid;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "meta_title")
    private String metaTitle;

    @Column(name = "meta_description", columnDefinition = "TEXT")
    private String metaDescription;

    @Column(name = "canonical_url", length = 500)
    private String canonicalUrl;

    @Column(name = "meta_keywords", length = 500)
    private String metaKeywords;

    @Column(name = "og_title")
    private String ogTitle;

    @Column(name = "og_description", columnDefinition = "TEXT")
    private String ogDescription;

    @Column(name = "og_image", length = 500)
    private String ogImage;

    @Column(name = "twitter_card", length = 100)
    private String twitterCard;

    @Column(name = "json_ld", columnDefinition = "TEXT")
    private String jsonLd;

    @Column(name = "robot_index", nullable = false)
    private Boolean robotIndex = true;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "date_created", nullable = false, updatable = false)
    private LocalDateTime dateCreated;

    @Column(name = "date_updated", nullable = false)
    private LocalDateTime dateUpdated;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

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

