package org.psint.beyosclothing.modules.resellers.service;

import java.util.Collection;
import java.util.Map;

/**
 * Service interface for resolving reseller display names directly from the reseller datasource.
 * Replaces the RabbitMQ RPC lookup previously used by the admin order listing, avoiding
 * per-request broker round-trips.
 */
public interface ResellerNameLookupService {

    /**
     * Resolve the display name of a single reseller.
     *
     * @param resellerId Reseller ID
     * @return the reseller's full name, or null when the reseller does not exist
     */
    String getResellerName(Long resellerId);

    /**
     * Resolve display names for a batch of resellers in a single query.
     *
     * @param resellerIds Reseller IDs (duplicates and nulls are ignored)
     * @return map of reseller ID to full name; IDs without a matching reseller are absent
     */
    Map<Long, String> getResellerNames(Collection<Long> resellerIds);
}
