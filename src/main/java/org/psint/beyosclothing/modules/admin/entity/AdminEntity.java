package org.psint.beyosclothing.modules.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.psint.beyosclothing.core.audit.BaseEntity;

import java.util.UUID;

/**
 * Admin Entity
 * Table: admin in beyos_admin_db
 * Stores admin staff information
 * Linked to User entity in auth DB via user_id
 */
@Entity
@Table(name = "admin", indexes = {
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_uuid", columnList = "uuid"),
    @Index(name = "idx_email", columnList = "email")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class    AdminEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, unique = true, length = 36)
    private String uuid; // Auto-generated UUID

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId; // Reference to users table in AUTH module (NOT FK)

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "email", length = 255)
    private String email; // ✅ Manager, Cashier, Inventory Officer, etc.

    @PrePersist
    public void generateUuid() {
        if (uuid == null) {
            uuid = UUID.randomUUID().toString();
        }
    }

    /**
     * Helper method to get full name
     */
    public String getFullName() {
        if (firstName == null && lastName == null) {
            return "";
        }
        if (firstName == null) {
            return lastName;
        }
        if (lastName == null) {
            return firstName;
        }
        return firstName + " " + lastName;
    }
}

