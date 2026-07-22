package org.psint.beyosclothing.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Shared Request DTO for customer address lookup
 * Used by Payment module (sender) and Customer module (receiver) via RabbitMQ
 * MUST be in shared package to avoid __TypeId__ class mismatch
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerAddressLookupRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private String requestId;
    private Long addressId;
    private Long customerId; // Optional: for validation
}
