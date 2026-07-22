package org.psint.beyosclothing.modules.pos.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * POS Cashier Entity
 * Database: beyos_pos
 * Table: pos_cashiers
 * Represents staff members who operate POS terminals
 */
@Entity
@Table(name = "pos_cashiers", indexes = {
        @Index(name = "idx_uuid", columnList = "uuid"),
        @Index(name = "idx_user_id", columnList = "user_id"),
        @Index(name = "idx_is_active", columnList = "is_active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PosCashierEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", unique = true, nullable = false, length = 36)
    private String uuid;

    @Column(name = "user_id")
    private Long userId; // FK to admin users (nullable for standalone cashiers)

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "pin_code", length = 255)
    private String pinCode; // Optional encrypted PIN for quick login

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

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

    /**
     * Helper method to check if cashier has PIN set
     */
    public boolean hasPin() {
        return pinCode != null && !pinCode.isEmpty();
    }
}

