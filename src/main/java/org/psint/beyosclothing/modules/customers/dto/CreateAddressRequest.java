package org.psint.beyosclothing.modules.customers.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Create Address Request DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAddressRequest {

    @NotBlank(message = "Address line 1 is required")
    private String addressLine1;

    private String addressLine2;

    @NotBlank(message = "City is required")
    private String city;

    private String district;

    private String province;

    @NotBlank(message = "Country is required")
    private String country;

    private String postalCode;

    private Boolean isDefault;
}

