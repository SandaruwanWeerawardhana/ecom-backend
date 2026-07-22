package org.psint.beyosclothing.modules.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.psint.beyosclothing.core.audit.BaseEntity;

/**
 * User Entity
 * Table: users in beyos_auth_db
 * Note: UserType determines if user is ADMIN, CUSTOMER, or RESELLER
 * For ADMIN users, userRoleId references specific admin role (SUPER_ADMIN, MANAGER, etc.)
 */
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_username", columnList = "username"),
    @Index(name = "idx_email", columnList = "email"),
    @Index(name = "idx_user_type", columnList = "user_type"),
    @Index(name = "idx_user_role_id", columnList = "user_role_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, unique = true, length = 100)
    private String username; // Auto-generated: e.g., USR8k3x2p9qm5n7q8w2

    @Column(name = "email", nullable = false, length = 255)
    private String email; // Used for login - not unique to allow reusing deactivated emails

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Column(name = "user_type", nullable = false, length = 50)
    private String userType; // ADMIN, CUSTOMER, RESELLER

    @Column(name = "user_role_id")
    private Long userRoleId; // ✅ For ADMIN users - references user_role table

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private Boolean emailVerified = false;

    @Column(name = "account_locked", nullable = false)
    @Builder.Default
    private Boolean accountLocked = false;

    @Column(name = "login_attempts")
    @Builder.Default
    private Integer loginAttempts = 0;
}
