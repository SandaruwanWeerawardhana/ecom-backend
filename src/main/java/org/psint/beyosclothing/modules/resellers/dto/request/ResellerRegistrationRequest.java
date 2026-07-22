package org.psint.beyosclothing.modules.resellers.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

/**
 * DTO for Reseller Registration Request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request body for reseller registration")
public class ResellerRegistrationRequest {

    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name must not exceed 100 characters")
    @Schema(description = "Reseller's first name", example = "John")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100, message = "Last name must not exceed 100 characters")
    @Schema(description = "Reseller's last name", example = "Doe")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Schema(description = "Reseller's email address", example = "john.doe@example.com")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    @Schema(description = "Account password", example = "SecurePass123!")
    private String password;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[0-9]{10,15}$", message = "Phone number must be 10-15 digits")
    @Schema(description = "Reseller's phone number", example = "0771234567")
    private String phone;

    @NotBlank(message = "Address line 1 is required")
    @Size(max = 255, message = "Address line 1 must not exceed 255 characters")
    @Schema(description = "Primary address line", example = "123 Main Street")
    private String addressLine1;

    @Size(max = 255, message = "Address line 2 must not exceed 255 characters")
    @Schema(description = "Secondary address line", example = "Apartment 4B")
    private String addressLine2;

    @NotBlank(message = "City is required")
    @Size(max = 100, message = "City must not exceed 100 characters")
    @Schema(description = "City", example = "Colombo")
    private String city;

    @NotBlank(message = "District is required")
    @Size(max = 100, message = "District must not exceed 100 characters")
    @Schema(description = "District", example = "Colombo")
    private String district;

    @NotBlank(message = "Province is required")
    @Size(max = 100, message = "Province must not exceed 100 characters")
    @Schema(description = "Province", example = "Western")
    private String province;

    @Size(max = 20, message = "Postal code must not exceed 20 characters")
    @Schema(description = "Postal code", example = "00100")
    private String postalCode;
}

