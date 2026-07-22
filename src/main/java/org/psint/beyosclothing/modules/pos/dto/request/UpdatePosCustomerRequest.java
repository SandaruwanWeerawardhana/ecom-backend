package org.psint.beyosclothing.modules.pos.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePosCustomerRequest {

    @Size(max = 100)
    private String fullName;

    @Pattern(regexp = "POS|ONLINE", message = "source must be POS or ONLINE")
    private String source;

    @Size(max = 15)
    private String phone;

    @Size(max = 255)
    private String address;

    @Size(max = 30)
    private String city;

    @Size(max = 30)
    private String province;

    @Size(max = 30)
    private String district;

    @Size(max = 10)
    private String zipCode;
}
