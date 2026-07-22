package org.psint.beyosclothing.modules.payment.dto.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Request DTO for looking up customer delivery address
 * Sent to Customer module via RabbitMQ
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerAddressLookupRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private String requestId;
    private Long addressId;
    private Long customerId; // For validation
}
