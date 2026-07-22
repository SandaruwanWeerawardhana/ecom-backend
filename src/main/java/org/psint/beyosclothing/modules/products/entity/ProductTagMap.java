package org.psint.beyosclothing.modules.products.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Product Tag Map Entity (Junction Table)
 * Database: beyos_product_db
 * Table: product_tag_map
 */
@Entity
@Table(name = "product_tag_map",
    indexes = {
        @Index(name = "idx_product_id", columnList = "product_id"),
        @Index(name = "idx_tag_id", columnList = "tag_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "unique_product_tag", columnNames = {"product_id", "tag_id"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductTagMap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", unique = true, nullable = false, length = 36)
    private String uuid;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "tag_id", nullable = false)
    private Long tagId;

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
}

