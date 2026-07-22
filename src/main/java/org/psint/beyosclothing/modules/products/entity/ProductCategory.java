package org.psint.beyosclothing.modules.products.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Product Category Entity
 * Database: beyos_product_db
 * Table: product_categories
 */
@Entity
@Table(name = "product_categories", indexes = {
    @Index(name = "idx_parent_id", columnList = "parent_id"),
    @Index(name = "idx_slug", columnList = "slug"),
    @Index(name = "idx_name", columnList = "name")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", unique = true, nullable = false, length = 36)
    private String uuid;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "slug", unique = true, nullable = false)
    private String slug;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "category_image", columnDefinition = "LONGTEXT")
    private String imageUrl;

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
        // Auto-generate slug from name if not provided
        if (slug == null || slug.isBlank()) {
            slug = generateSlug(name);
        }
    }

    @PreUpdate
    protected void onUpdate() {
        dateUpdated = LocalDateTime.now();
    }

    /**
     * Generate URL-friendly slug from category name
     */
    private String generateSlug(String text) {
        return text.toLowerCase()
                .trim()
                .replaceAll("[^a-z0-9\\s-]", "") // Remove special characters
                .replaceAll("\\s+", "-")           // Replace spaces with hyphens
                .replaceAll("-+", "-")             // Replace multiple hyphens with single
                .replaceAll("^-|-$", "");          // Remove leading/trailing hyphens
    }
}
