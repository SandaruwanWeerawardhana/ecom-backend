package org.psint.beyosclothing.modules.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.psint.beyosclothing.core.audit.BaseEntity;

import java.util.HashSet;
import java.util.Set;

/**
 * User Role Entity
 * Table: user_role in beyos_auth_db
 * Supports both user types and admin-specific roles
 */
@Entity
@Table(name = "user_role", indexes = {
    @Index(name = "idx_role_name", columnList = "role_name"),
    @Index(name = "idx_role_code", columnList = "role_code"),
    @Index(name = "idx_user_type", columnList = "user_type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserRole extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "role_code", unique = true, length = 100)
    private String roleCode; // e.g., "SUPER_ADMIN", "MANAGER", "CASHIER"

    @Column(name = "role_name", nullable = false, unique = true, length = 200)
    private String roleName; // e.g., "Super Administrator", "Manager"

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "token", unique = true, length = 100)
    private String token;

    @Column(name = "user_type", length = 50)
    private String userType; // ADMIN, CUSTOMER, RESELLER

    @OneToMany(mappedBy = "userRole", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<RolePermission> rolePermissions = new HashSet<>();
}
