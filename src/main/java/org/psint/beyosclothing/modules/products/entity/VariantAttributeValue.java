package org.psint.beyosclothing.modules.products.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Variant Attribute Values Entity (Junction Table)
 * Database: beyos_product_db
 * Table: variant_attribute_values
 */
@Entity
@Table(name = "variant_attribute_values",
    indexes = {
        @Index(name = "idx_variant_id", columnList = "variant_id"),
        @Index(name = "idx_attribute_value_id", columnList = "attribute_value_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "unique_variant_attribute", columnNames = {"variant_id", "attribute_value_id"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VariantAttributeValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", unique = true, nullable = false, length = 36)
    private String uuid;

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @Column(name = "attribute_value_id", nullable = false)
    private Long attributeValueId;

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
}

