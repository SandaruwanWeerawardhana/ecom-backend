package org.psint.beyosclothing.modules.pos.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * POS Terminal Entity
 * Database: beyos_pos
 * Table: pos_terminals
 * Represents physical POS devices and counters
 */
@Entity
@Table(name = "pos_terminals", indexes = {
        @Index(name = "idx_uuid", columnList = "uuid"),
        @Index(name = "idx_code", columnList = "code"),
        @Index(name = "idx_is_active", columnList = "is_active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PosTerminalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", unique = true, nullable = false, length = 36)
    private String uuid;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "code", nullable = false, length = 255)
    private String code;

    @Column(name = "location", length = 255)
    private String location;

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
}
