package org.psint.beyosclothing.modules.orders.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Order Entity
 * Database: beyos_order
 * Table: orders
 */
@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_uuid", columnList = "uuid"),
        @Index(name = "idx_customer_id", columnList = "customer_id"),
        @Index(name = "idx_reseller_id", columnList = "reseller_id"),
        @Index(name = "idx_order_number", columnList = "order_number"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_payment_status", columnList = "payment_status"),
        @Index(name = "idx_created_at", columnList = "created_at"),
        @Index(name = "idx_source", columnList = "source"),
        @Index(name = "idx_pos_terminal_id", columnList = "pos_terminal_id"),
        @Index(name = "idx_pos_cashier_id", columnList = "pos_cashier_id"),
        @Index(name = "idx_orders_source_created_at", columnList = "source, created_at"),
        @Index(name = "idx_cart_id", columnList = "cart_id"),
        @Index(name = "idx_orders_status_created_subtotal", columnList = "status, created_at, subtotal")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", unique = true, nullable = false, length = 36)
    private String uuid;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "reseller_id")
    private Long resellerId;

    @Column(name = "cart_id")
    private Long cartId;

    @Column(name = "order_number", unique = true, nullable = false, length = 50)
    private String orderNumber;

    @Column(name = "way_bill_id", length = 100)
    private String wayBillId;

    @Column(name = "pickup_id")
    private Long pickupId; // Pickup ID from courier (Koombiyo)

    // POS Support Fields
    @Enumerated(EnumType.STRING)
    @Column(name = "source")
    private OrderSource source = OrderSource.ONLINE;

    @Column(name = "pos_terminal_id")
    private Long posTerminalId;

    @Column(name = "pos_cashier_id")
    private Long posCashierId;

    @Column(name = "card_last_four_digits", length = 4)
    private String cardLastFourDigits; // For card payment tracking

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private OrderStatus status = OrderStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status")
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "payment_method_id")
    private Long paymentMethodId; // Reference to payment method (NOT FK - cross-module)

    @Column(name = "payment_reference", length = 100)
    private String paymentReference;

    @Column(name = "subtotal", nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "discount_total", precision = 10, scale = 2)
    private BigDecimal discountTotal = BigDecimal.ZERO;

    @Column(name = "shipping_cost", precision = 10, scale = 2)

    private BigDecimal shippingCost = BigDecimal.ZERO;

    @Column(name = "shipping_weight", precision = 10, scale = 3)
    private BigDecimal shippingWeight = BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "shipping_breakdown", columnDefinition = "JSON")
    private Map<String, Object> shippingBreakdown;

    @Enumerated(EnumType.STRING)
    @Column(name = "shipping_payer")
    private ShippingPayer shippingPayer = ShippingPayer.CUSTOMER;

    @Column(name = "shipment_id")
    private Long shipmentId;

    @Column(name = "total", nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    @Column(name = "promo_code", length = 100)
    private String promoCode;

    @Column(name = "promo_discount", precision = 10, scale = 2)
    private BigDecimal promoDiscount;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (uuid == null) {
            uuid = java.util.UUID.randomUUID().toString();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum OrderStatus {
        PENDING,
        PAID,
        PROCESSING,
        COURIER_ORDER_PLACED,
        OUT_FOR_DELIVERY,
        DELIVERED,
        COMPLETED,
        CANCELLED,
        REJECTED,
        RETURN_REQUESTED,
        RETURN_APPROVED,
        REFUND_INITIATED,
        REFUNDED
    }

    public enum PaymentStatus {
        UNPAID,
        PAID,
        FAILED,
        REFUNDED
    }

    public enum ShippingPayer {
        CUSTOMER,
        RESELLER
    }

    public enum OrderSource {
        ONLINE,
        POS
    }
}
