package org.psint.beyosclothing.modules.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.psint.beyosclothing.core.audit.BaseEntity;

import java.time.LocalDateTime;

/**
 * Admin Notifications Entity
 * Table: admin_notifications in beyos_admin_db
 * Send notifications to admin staff
 */
@Entity
@Table(name = "admin_notifications", indexes = {
    @Index(name = "idx_admin_id", columnList = "admin_id"),
    @Index(name = "idx_is_read", columnList = "is_read"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminNotificationEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id")
    private AdminEntity admin;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}

