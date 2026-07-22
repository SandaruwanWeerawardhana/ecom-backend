package org.psint.beyosclothing.modules.customers.dto.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Response DTO for customer address lookup
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerAddressLookupResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private String requestId;
    private Boolean found;
    private Long addressId;
    private String fullName;
    private String phoneNumber;
    private String email;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String province;
    private String postalCode;
    private String country;
}

