package org.psint.beyosclothing.modules.cart.dto.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Response from Customer module with customer details
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerLookupResponse implements Serializable {
    private String requestId;
    private Long customerId; // Internal ID
    private String customerUuid;
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private Boolean found;
}

