package org.psint.beyosclothing.modules.cart.dto.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Request to lookup customer by UUID
 * Sent to Customer module via RabbitMQ
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerLookupRequest implements Serializable {
    private String customerUuid;
    private String requestId; // For tracking the request
}

