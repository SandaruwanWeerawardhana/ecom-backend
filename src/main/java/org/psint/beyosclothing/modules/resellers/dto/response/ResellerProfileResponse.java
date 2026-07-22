package org.psint.beyosclothing.modules.resellers.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for Reseller Profile Response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Reseller profile information")
public class ResellerProfileResponse {

    @Schema(description = "Reseller UUID", example = "123e4567-e89b-12d3-a456-426614174000")
    private String uuid;

    @Schema(description = "First name", example = "John")
    private String firstName;

    @Schema(description = "Last name", example = "Doe")
    private String lastName;

    @Schema(description = "Full name", example = "John Doe")
    private String fullName;

    @Schema(description = "Email address", example = "john.doe@example.com")
    private String email;

    @Schema(description = "Profile image URL", example = "https://example-bucket.s3.amazonaws.com/resellers/uuid/profile.jpg")
    private String imageUrl;

    @Schema(description = "Phone number", example = "0771234567")
    private String phone;

    @Schema(description = "Address line 1", example = "123 Main Street")
    private String addressLine1;

    @Schema(description = "Address line 2", example = "Apartment 4B")
    private String addressLine2;

    @Schema(description = "City", example = "Colombo")
    private String city;

    @Schema(description = "District", example = "Colombo")
    private String district;

    @Schema(description = "Province", example = "Western")
    private String province;

    @Schema(description = "Postal code", example = "00100")
    private String postalCode;

    @Schema(description = "Reseller status", example = "APPROVED")
    private String status;

    @Schema(description = "Status message for frontend", example = "Your account is approved and active")
    private String statusMessage;

    @Schema(description = "Can override prices", example = "true")
    private Boolean allowPriceOverride;

    @Schema(description = "Minimum markup percentage", example = "10.00")
    private BigDecimal minAllowedMarkupPct;

    @Schema(description = "Maximum markup percentage", example = "50.00")
    private BigDecimal maxAllowedMarkupPct;

    @Schema(description = "Current credit balance", example = "15000.00")
    private BigDecimal creditBalance;

    @Schema(description = "Credit limit", example = "50000.00")
    private BigDecimal creditLimit;

    @Schema(description = "Available credit", example = "35000.00")
    private BigDecimal availableCredit;

    @Schema(description = "Registration date", example = "2026-01-15T10:30:00")
    private LocalDateTime registrationDate;

    @Schema(description = "Approval date", example = "2026-01-16T14:20:00")
    private LocalDateTime approvalDate;

    @Schema(description = "Account active status", example = "true")
    private Boolean isActive;
}

