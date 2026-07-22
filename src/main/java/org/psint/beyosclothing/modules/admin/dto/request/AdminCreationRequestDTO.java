package org.psint.beyosclothing.modules.admin.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Admin Creation Request DTO
 * Used when creating a new admin user
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminCreationRequestDTO {

    @NotBlank(message = "First name is required")
    @Size(min = 2, max = 50, message = "First name must be between 2 and 50 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(min = 2, max = 50, message = "Last name must be between 2 and 50 characters")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=]).*$",
            message = "Password must contain at least one digit, one lowercase, one uppercase, and one special character")
    private String password;

    @Pattern(regexp = "^[0-9]{10,15}$", message = "Phone number must be between 10 and 15 digits")
    private String phone;

    @NotBlank(message = "Role code is required")
    private String roleCode; // e.g., "SUPER_ADMIN", "MANAGER", "CASHIER"
}

