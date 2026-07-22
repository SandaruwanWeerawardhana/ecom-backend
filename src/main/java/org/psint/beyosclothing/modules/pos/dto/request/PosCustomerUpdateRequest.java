package org.psint.beyosclothing.modules.pos.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PosCustomerUpdateRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long customerId;
    private String fullName;
    private String phone;
    private String address;
    private String city;
    private String province;
    private String district;
    private String zipCode;
}
