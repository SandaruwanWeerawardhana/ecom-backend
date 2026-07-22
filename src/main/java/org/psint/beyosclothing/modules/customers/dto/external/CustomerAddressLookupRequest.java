package org.psint.beyosclothing.modules.customers.dto.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Request DTO for customer address lookup (from Order module)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerAddressLookupRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private String requestId;
    private Long customerId;
    private Long addressId;
}

