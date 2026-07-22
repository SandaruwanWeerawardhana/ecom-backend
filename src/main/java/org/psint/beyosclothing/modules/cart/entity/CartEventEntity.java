package org.psint.beyosclothing.modules.cart.entity;

import jakarta.persistence.*;
import lombok.*;
import org.psint.beyosclothing.core.audit.BaseEntity;

import java.util.UUID;

/**
 * Cart Event Entity
 * Table: cart_events in beyos_cart_db
 * Stores all cart-related events for debugging and analytics
 */
@Entity
@Table(name = "cart_events", indexes = {
    @Index(name = "idx_uuid", columnList = "uuid"),
    @Index(name = "idx_cart_id", columnList = "cart_id"),
    @Index(name = "idx_event_type", columnList = "event_type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartEventEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, unique = true, length = 36)
    private String uuid;

    @Column(name = "cart_id")
    private Long cartId; // FK to carts table (nullable for some events)

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType; // ITEM_ADDED, ITEM_REMOVED, PROMO_APPLIED, CART_EXPIRED, CART_MERGED_AFTER_LOGIN

    @Column(name = "event_data", columnDefinition = "JSON")
    private String eventData; // JSON data about the event

    @PrePersist
    public void generateUuid() {
        if (uuid == null) {
            uuid = UUID.randomUUID().toString();
        }
    }
}

