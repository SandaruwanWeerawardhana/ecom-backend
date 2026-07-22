package org.psint.beyosclothing.modules.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.psint.beyosclothing.core.audit.BaseEntity;

import java.time.LocalDateTime;

/**
 * Admin Activity Logs Entity
 * Table: admin_activity_logs in beyos_admin_db
 * Tracks what admin staff do for auditing purposes
 */
@Entity
@Table(name = "admin_activity_logs", indexes = {
    @Index(name = "idx_admin_id", columnList = "admin_id"),
    @Index(name = "idx_module", columnList = "module"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminActivityLogEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id", nullable = false)
    private AdminEntity admin;

    @Column(name = "action", nullable = false, length = 255)
    private String action; // e.g., "UPDATED_ORDER_STATUS", "ADDED_PRODUCT"

    @Column(name = "module", nullable = false, length = 100)
    private String module; // PRODUCT, ORDER, INVENTORY, DELIVERY, ADMIN, etc.

    @Column(name = "reference_id")
    private Long referenceId; // order_id / product_id / admin_id / etc.

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}

