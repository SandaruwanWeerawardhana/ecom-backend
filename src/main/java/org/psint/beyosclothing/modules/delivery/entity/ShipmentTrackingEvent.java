package org.psint.beyosclothing.modules.delivery.entity;

import jakarta.persistence.*;
import lombok.*;
import org.psint.beyosclothing.core.audit.BaseEntity;

import java.time.LocalDateTime;
import java.util.UUID;

@Table(name = "shipment_tracking_events", indexes = {
        @Index(name = "idx_shipment_tracking_uuid", columnList = "uuid", unique = true),
        @Index(name = "idx_shipment_tracking_shipment", columnList = "shipment_id"),
        @Index(name = "idx_shipment_tracking_event_time", columnList = "event_time"),
        @Index(name = "idx_shipment_tracking_status", columnList = "status")
})
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentTrackingEvent extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", unique = true, nullable = false, length = 36)
    private String uuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_id", nullable = false, foreignKey = @ForeignKey(name = "fk_shipment_tracking_shipment"))
    private Shipment shipment;

    @Column(name = "event_time", nullable = false)
    private LocalDateTime eventTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Shipment.ShipmentStatus status;

    @Column(name = "location")
    private String location;

    @Column(name = "description")
    private String description;

    @Column(name = "event_source", length = 50)
    private String eventSource; // e.g., "COURIER", "SYSTEM", "MANUAL", "PICKUP_REQUEST"

    @Column(name = "raw_payload", columnDefinition = "json")
    private String rawPayload;

    @PrePersist
    protected void generateUuid() {
        if (uuid == null) {
            uuid = UUID.randomUUID().toString();
        }
        if (eventTime == null) {
            eventTime = LocalDateTime.now();
        }
    }
}
