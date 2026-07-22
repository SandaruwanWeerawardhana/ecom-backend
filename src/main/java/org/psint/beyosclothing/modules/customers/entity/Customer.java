package org.psint.beyosclothing.modules.customers.entity;

import jakarta.persistence.*;
import lombok.*;
import org.psint.beyosclothing.core.audit.BaseEntity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Customer Entity
 * Table: customers in beyos_customers_db
 * Note: user_id is NOT a foreign key - it's a reference to auth DB
 * Email is stored in User table (auth DB) - NOT here
 */
@Entity
@Table(name = "customers", indexes = {
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_phone_number", columnList = "phone_number"),
    @Index(name = "idx_customers_is_active", columnList = "is_active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId; // Reference to auth DB - NOT FK

    @Column(name = "uuid", nullable = false, unique = true, length = 36)
    private String uuid;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "profile_image", length = 255)
    private String profileImage;

    @Column(name = "is_phone_number_verified", nullable = false)
    @Builder.Default
    private Boolean isPhoneNumberVerified = false;

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<Address> addresses = new HashSet<>();

    
    /**
     * Auto-generate UUID before persisting
     * Follows the same pattern as Cart entities
     */
    @PrePersist
    public void generateUuid() {
        if (uuid == null) {
            uuid = UUID.randomUUID().toString();
        }
    }
    /**
     * Helper method to get full name
     * Combines firstName and lastName
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

    /**
     * Helper method to get phone
     * Returns phoneNumber field
     */
    public String getPhone() {
        return phoneNumber;
    }

    /**
     * Transient field for email (fetched from User entity when needed)
     * Not stored in this table - stored in User table (auth DB)
     */
    @Transient
    private String email;

    /**
     * Helper method to get email
     * Returns the transient email field
     */
    public String getEmail() {
        return email;
    }

    /**
     * Helper method to set email
     * Sets the transient email field
     */
    public void setEmail(String email) {
        this.email = email;
    }
}
