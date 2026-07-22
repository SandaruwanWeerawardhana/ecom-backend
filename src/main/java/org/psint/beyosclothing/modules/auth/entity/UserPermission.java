package org.psint.beyosclothing.modules.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.psint.beyosclothing.core.audit.BaseEntity;

import java.util.HashSet;
import java.util.Set;

/**
 * User Permission Entity (Hierarchical)
 * Table: user_permission in beyos_auth_db
 * Supports parent-child permission structure for fine-grained access control
 * Example: ADMIN_MANAGE (parent) -> CREATE_ADMIN, EDIT_ADMIN (children)
 */
@Entity
@Table(name = "user_permission", indexes = {
    @Index(name = "idx_permission_code", columnList = "permission_code"),
    @Index(name = "idx_permission_name", columnList = "permission_name"),
    @Index(name = "idx_parent_id", columnList = "parent_id"),
    @Index(name = "idx_module", columnList = "module")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPermission extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "permission_code", nullable = false, unique = true, length = 100)
    private String permissionCode; // e.g., "ADMIN_MANAGE", "CREATE_ADMIN"

    @Column(name = "permission_name", nullable = false, length = 200)
    private String permissionName; // e.g., "Admin Management", "Create Admin"

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "module", nullable = false, length = 100)
    private String module; // e.g., "ADMIN", "PRODUCT", "ORDER", "INVENTORY"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private UserPermission parent; // ✅ Hierarchical structure

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<UserPermission> subPermissions = new HashSet<>();

    @Column(name = "is_parent", nullable = false)
    @Builder.Default
    private Boolean isParent = false;

    @Column(name = "display_order")
    private Integer displayOrder;
}
