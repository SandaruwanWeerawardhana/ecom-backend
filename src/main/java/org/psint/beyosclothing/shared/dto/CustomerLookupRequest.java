package org.psint.beyosclothing.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Shared DTO for Customer Lookup Request
 * Used by multiple modules to avoid class duplication
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerLookupRequest {
    private String requestId;
    private String customerUuid;
}

