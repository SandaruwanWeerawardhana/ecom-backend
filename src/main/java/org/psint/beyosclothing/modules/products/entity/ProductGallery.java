package org.psint.beyosclothing.modules.products.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Product Gallery Entity
 * Database: beyos_product_db
 * Table: product_gallery
 */
@Entity
@Table(name = "product_gallery", indexes = {
    @Index(name = "idx_product_id", columnList = "product_id"),
    @Index(name = "idx_sort_order", columnList = "sort_order")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductGallery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", unique = true, nullable = false, length = 36)
    private String uuid;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "media_name", nullable = false)
    private String mediaName;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 20)
    private MediaType mediaType = MediaType.IMAGE;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "alt_text")
    private String altText;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

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
    }

    public enum MediaType {
        IMAGE, VIDEO
    }
}

