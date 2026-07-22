package org.psint.beyosclothing.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Shared DTO for Customer Lookup Response
 * Used by multiple modules to avoid class duplication and ClassCastException
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerLookupResponse {
    private String requestId;
    private Boolean found;
    private Long customerId;
    private String customerUuid;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String customerType; // CUSTOMER or RESELLER
}

