package org.psint.beyosclothing.modules.products.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Product Payment Method Mapping Entity
 * Database: beyos_product_db
 * Table: product_payment_method_mappings
 *
 * Maps which payment methods are allowed for each product.
 * No cross-database FK — payment_method_id is a logical reference only.
 */
@Entity
@Table(name = "product_payment_method_mappings", indexes = {
    @Index(name = "idx_ppmm_product_id", columnList = "product_id"),
    @Index(name = "idx_ppmm_payment_method_id", columnList = "payment_method_id"),
    @Index(name = "idx_ppmm_uuid", columnList = "uuid"),
    @Index(name = "idx_ppmm_product_active", columnList = "product_id, is_active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductPaymentMethodMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", unique = true, nullable = false, length = 36)
    private String uuid;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    /**
     * Logical reference to beyos_payment.payment_methods — no FK constraint.
     */
    @Column(name = "payment_method_id", nullable = false)
    private Long paymentMethodId;

    /**
     * Snapshot of payment method code (e.g. COD, BANK_CARD).
     * Stored for display/filtering without cross-DB joins.
     */
    @Column(name = "payment_method_code", nullable = false, length = 50)
    private String paymentMethodCode;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
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
            uuid = UUID.randomUUID().toString();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        dateUpdated = LocalDateTime.now();
    }
}

