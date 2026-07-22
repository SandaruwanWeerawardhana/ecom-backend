package org.psint.beyosclothing.modules.pos.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a POS customer
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePosCustomerRequest {

    @NotBlank
    @Size(max = 100)
    private String fullName;

    @NotBlank
    @Size(max = 15)
    private String phone;

    @NotBlank
    @Size(max = 255)
    private String address;

    @NotBlank
    @Size(max = 30)
    private String city;

    @NotBlank
    @Size(max = 30)
    private String province;

    @Size(max = 30)
    private String district;

    @Size(max = 10)
    private String zipCode;
}

