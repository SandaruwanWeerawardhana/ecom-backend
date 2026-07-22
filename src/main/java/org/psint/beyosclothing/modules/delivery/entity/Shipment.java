package org.psint.beyosclothing.modules.delivery.entity;

import jakarta.persistence.*;
import lombok.*;
import org.psint.beyosclothing.core.audit.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Table(name = "shipments", indexes = {
        @Index(name = "idx_shipment_uuid", columnList = "uuid", unique = true),
        @Index(name = "idx_shipment_order", columnList = "order_id"),
        @Index(name = "idx_shipment_courier", columnList = "courier_id"),
        @Index(name = "idx_shipment_status", columnList = "status"),
        @Index(name = "idx_shipment_tracking", columnList = "tracking_number")
})
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Shipment extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", unique = true, nullable = false, length = 36)
    private String uuid;

    @Column(name = "order_id")
    private Long orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "courier_id", nullable = false, foreignKey = @ForeignKey(name = "fk_shipment_courier"))
    private Courier courier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "courier_rate_id", foreignKey = @ForeignKey(name = "fk_shipment_courier_rate"))
    private CourierRate courierRate;

    @Column(name = "shipment_weight", precision = 10, scale = 3)
    private BigDecimal shipmentWeight;

    @Column(name = "shipping_cost", precision = 12, scale = 2)
    private BigDecimal shippingCost;

    @Column(name = "shipping_breakdown", columnDefinition = "json")
    private String shippingBreakdown;

    @Column(name = "payment_method_id", nullable = true)
    private Long paymentMethodId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payer_type", nullable = false)
    private PayerType payerType = PayerType.CUSTOMER;

    @Column(name = "tracking_number")
    private String trackingNumber;

    @Column(name = "way_bill_id", length = 100)
    private String wayBillId;

    @Column(name = "tracking_url")
    private String trackingUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ShipmentStatus status = ShipmentStatus.PENDING;

    @Column(name = "booked_at")
    private LocalDateTime bookedAt;

    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @PrePersist
    protected void generateUuid() {
        if (uuid == null) {
            uuid = UUID.randomUUID().toString();
        }
    }

    public enum ShipmentStatus {
        // Initial states
        PENDING,
        BOOKED,
        REQUESTED_PICK_UP,

        // Processing states
        PROCESSING,
        PICKED,
        ON_QC,

        // In transit states
        IN_TRANSIT,
        COLLECTED_BY_KOOMBIYO,
        DISPATCH_TO_DESTINATION,
        RECEIVED_AT_DESTINATION,

        // Out for delivery
        OUT_FOR_DELIVERY,

        // Delivery states
        DELIVERED,
        DELIVERED_NOT_CONFIRMED,
        PARTIALLY_DELIVERED,
        PARTIALLY_DELIVERED_NOT_CONFIRMED,
        CLIENT_RECEIVED,

        // Failed/Problem states
        FAILED_TO_DELIVER,
        RESCHEDULED,

        // Return states
        RETURN_TO_CLIENT,
        RETURN_TO_HO,
        EXCHANGE_COLLECTED,
        EXCHANGE_RECEIVED,

        // Different destination
        DIFFERENT_DESTINATION,
        PENDING_DIFFERENT_DESTINATION,

        // Warehouse states
        RECEIVED_AT_HO,
        RECEIVED_AT_WAREHOUSE,

        // Other states
        HOLD,
        PURCHASE_BY_KOOMBIYO,
        CONFIRMED_BY_BRANCH,

        // Final states
        FAILED,
        RETURNED
    }

    public enum PayerType {
        CUSTOMER, RESELLER
    }
}
