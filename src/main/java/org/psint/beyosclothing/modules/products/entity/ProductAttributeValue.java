package org.psint.beyosclothing.modules.products.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Product Attribute Value Entity
 * Database: beyos_product_db
 * Table: product_attribute_values
 */
@Entity
@Table(name = "product_attribute_values",
    indexes = {
        @Index(name = "idx_attribute_id", columnList = "attribute_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "unique_attribute_value", columnNames = {"attribute_id", "value", "is_active"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductAttributeValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", unique = true, nullable = false, length = 36)
    private String uuid;

    @Column(name = "attribute_id", nullable = false)
    private Long attributeId;

    @Column(name = "value", nullable = false)
    private String value;

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
