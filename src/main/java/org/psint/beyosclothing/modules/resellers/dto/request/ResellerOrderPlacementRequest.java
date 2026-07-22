package org.psint.beyosclothing.modules.resellers.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

/**
 * DTO for Reseller Order Placement Request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to place order for reseller's customer")
public class ResellerOrderPlacementRequest {

    @NotBlank(message = "Customer name is required")
    @Size(max = 255, message = "Customer name must not exceed 255 characters")
    @Schema(description = "Customer's name", example = "Jane Smith")
    private String customerName;

    @NotBlank(message = "Customer phone is required")
    @Pattern(regexp = "^[0-9]{10,15}$", message = "Phone number must be 10-15 digits")
    @Schema(description = "Customer's phone number", example = "0771234567")
    private String customerPhone;

    @Email(message = "Invalid email format")
    @Schema(description = "Customer's email (optional)", example = "jane.smith@example.com")
    private String customerEmail;

    @NotNull(message = "Shipping address is required")
    @Schema(description = "Shipping address details")
    private ShippingAddress shippingAddress;

    @NotBlank(message = "Courier UUID is required")
    @Schema(description = "Selected courier method UUID", example = "123e4567-e89b-12d3-a456-426614174000")
    private String courierUuid;

    @Size(max = 500, message = "Notes must not exceed 500 characters")
    @Schema(description = "Additional notes (optional)", example = "Please deliver after 5 PM")
    private String notes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShippingAddress {
        @NotBlank(message = "Address line 1 is required")
        @Schema(description = "Address line 1", example = "456 Oak Avenue")
        private String line1;

        @Schema(description = "Address line 2", example = "Near City Mall")
        private String line2;

        @NotBlank(message = "City is required")
        @Schema(description = "City", example = "Kandy")
        private String city;

        @NotNull(message = "City ID is required")
        @Schema(description = "City ID (for internal use)", example = "10")
        private Integer cityId;


        @NotBlank(message = "District is required")
        @Schema(description = "District", example = "Kandy")
        private String district;

        @NotNull(message = "District ID is required")
        @Schema(description = "District ID (for internal use)", example = "100")
        private Integer districtId;

        @NotBlank(message = "Province is required")
        @Schema(description = "Province", example = "Central")
        private String province;

        @Schema(description = "Postal code", example = "20000")
        private String postalCode;
    }
}

