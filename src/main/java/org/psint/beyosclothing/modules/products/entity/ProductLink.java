package org.psint.beyosclothing.modules.products.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Product Links Entity (Cross-sell, Upsell, Related)
 * Database: beyos_product_db
 * Table: product_links
 */
@Entity
@Table(name = "product_links", indexes = {
    @Index(name = "idx_product_id", columnList = "product_id"),
    @Index(name = "idx_linked_id", columnList = "linked_id"),
    @Index(name = "idx_link_type", columnList = "link_type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", unique = true, nullable = false, length = 36)
    private String uuid;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "linked_id", nullable = false)
    private Long linkedId;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_type", nullable = false, length = 20)
    private LinkType linkType = LinkType.RELATED;

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

    public enum LinkType {
        UPSELL, CROSSSELL, RELATED
    }
}

