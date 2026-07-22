package org.psint.beyosclothing.modules.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.psint.beyosclothing.core.audit.BaseEntity;

import java.time.LocalDateTime;

/**
 * Admin Audit Trail Entity
 * Table: admin_audit_trail in beyos_admin_db
 * Logs before and after values for critical changes
 * Enterprise-grade compliance and dispute resolution
 */
@Entity
@Table(name = "admin_audit_trail", indexes = {
    @Index(name = "idx_admin_id", columnList = "admin_id"),
    @Index(name = "idx_module", columnList = "module"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminAuditTrailEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id", nullable = false)
    private AdminEntity admin;

    @Column(name = "module", nullable = false, length = 100)
    private String module; // PRODUCT, INVENTORY, DELIVERY, ADMIN, etc.

    @Column(name = "action", nullable = false, length = 100)
    private String action; // CREATE, UPDATE, DELETE

    @Column(name = "reference_id")
    private Long referenceId; // ID of the affected resource

    @Column(name = "before_data", columnDefinition = "JSON")
    private String beforeData; // JSON representation of data before change

    @Column(name = "after_data", columnDefinition = "JSON")
    private String afterData; // JSON representation of data after change

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}

