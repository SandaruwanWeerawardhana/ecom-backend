package org.psint.beyosclothing.modules.cart.entity;

import jakarta.persistence.*;
import lombok.*;
import org.psint.beyosclothing.core.audit.BaseEntity;

import java.util.UUID;

/**
 * Guest Cart Mapping Entity
 * Table: guest_cart_mapping in beyos_cart_db
 * Maps guest session tokens to guest carts for secure access
 * This prevents cart hijacking by not exposing guest_id directly
 */
@Entity
@Table(name = "guest_cart_mapping", indexes = {
    @Index(name = "idx_uuid", columnList = "uuid"),
    @Index(name = "idx_guest_session_token", columnList = "guest_session_token", unique = true),
    @Index(name = "idx_cart_id", columnList = "cart_id"),
    @Index(name = "idx_cart_uuid", columnList = "cart_uuid"),
    @Index(name = "idx_is_merged", columnList = "is_merged")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GuestCartMappingEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, unique = true, length = 36)
    private String uuid;

    @Column(name = "guest_session_token", nullable = false, unique = true, length = 255)
    private String guestSessionToken; // Secure token stored in HttpOnly cookie

    @Column(name = "cart_id", nullable = false)
    private Long cartId; // FK to carts table

    @Column(name = "cart_uuid", length = 36)
    private String cartUuid; // Cart UUID for reference

    @Column(name = "user_agent", length = 500)
    private String userAgent; // Browser info for security

    @Column(name = "ip_hash", length = 64)
    private String ipHash; // Hashed IP for fraud detection (not storing raw IP for privacy)

    @Column(name = "device_fingerprint", length = 255)
    private String deviceFingerprint; // Optional device fingerprint

    @Column(name = "is_merged", nullable = false)
    private Boolean isMerged; // True after cart is merged to customer account

    @PrePersist
    public void generateUuid() {
        if (uuid == null) {
            uuid = UUID.randomUUID().toString();
        }
        if (isMerged == null) {
            isMerged = false;
        }
    }
}

