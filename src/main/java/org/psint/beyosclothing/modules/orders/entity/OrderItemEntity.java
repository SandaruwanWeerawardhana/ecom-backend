package org.psint.beyosclothing.modules.orders.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Order Item Entity
 * Database: beyos_order
 * Table: order_items
 */
@Entity
@Table(name = "order_items", indexes = {
    @Index(name = "idx_order_id", columnList = "order_id"),
    @Index(name = "idx_product_id", columnList = "product_id"),
    @Index(name = "idx_variant_id", columnList = "variant_id"),
    @Index(name = "idx_source", columnList = "source")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // POS Support Field
    @Enumerated(EnumType.STRING)
    @Column(name = "source")
    private OrderItemSource source = OrderItemSource.ONLINE;

    @Column(name = "uuid", unique = true, nullable = false, length = 36)
    private String uuid;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "variant_id")
    private Long variantId;

    @Column(name = "product_title", nullable = false)
    private String productTitle;

    @Column(name = "variant_title")
    private String variantTitle;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "item_weight", precision = 10, scale = 3)
    private BigDecimal itemWeight;

    @Column(name = "item_total_weight", precision = 10, scale = 3)
    private BigDecimal itemTotalWeight;

    @Column(name = "is_refunded")
    private Boolean isRefunded = false;

    // Reseller Support Fields
    @Column(name = "unit_base_price", precision = 10, scale = 2)
    private BigDecimal unitBasePrice;

    @Column(name = "reseller_margin_amount", precision = 10, scale = 2)
    private BigDecimal resellerMarginAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (uuid == null) {
            uuid = java.util.UUID.randomUUID().toString();
        }
    }

    public enum OrderItemSource {
        ONLINE,
        POS
    }
}
