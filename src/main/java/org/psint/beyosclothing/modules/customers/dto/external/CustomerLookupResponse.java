package org.psint.beyosclothing.modules.customers.dto.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Response DTO for customer lookup
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerLookupResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private String requestId;
    private Boolean found;
    private Long customerId;
    private String customerUuid;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String customerType; // CUSTOMER or RESELLER
}

