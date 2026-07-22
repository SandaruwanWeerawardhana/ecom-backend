package org.psint.beyosclothing.modules.auth.dto.request.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * User Registration Request DTO
 * Username will be auto-generated - not required from user
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistrationRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email; // Used for login

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @NotBlank(message = "Role is required")
    private String role; // ADMIN, CUSTOMER, RESELLER

    // Customer-specific fields
    private String firstName;
    private String lastName;
    private String phoneNumber;

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
}
