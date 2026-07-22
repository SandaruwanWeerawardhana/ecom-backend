package org.psint.beyosclothing.modules.customers.dto.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request used by other modules (via RPC or messaging) to lookup customer details.
 * Supports filtering by active status, specific ids/uuids and pagination.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerDetailsLookupRequest {
    private String requestId; // correlation id

    // Filters
    private Boolean activeOnly; // if true, return only active customers

    // Optional explicit lookups - if provided, lookup only these customers
    private List<Long> customerIds;
    private List<String> customerUuids;

    // Pagination (optional). If null, caller expects either all or a safe default will be used.
    private Integer page;    // zero-based page index
    private Integer size;    // page size

    // Optional flag to include addresses in response
    private Boolean includeAddresses;
}
