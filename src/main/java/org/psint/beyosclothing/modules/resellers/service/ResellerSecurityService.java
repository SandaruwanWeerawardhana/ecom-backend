package org.psint.beyosclothing.modules.resellers.service;

import org.psint.beyosclothing.modules.resellers.entity.Reseller;

/**
 * Security service for reseller access control
 */
public interface ResellerSecurityService {

    /**
     * Validate reseller access and return reseller entity
     * @param userId User ID from JWT token
     * @return Reseller entity
     */
    Reseller validateResellerAccess(Long userId);

    /**
     * Check if reseller has approved status
     * @param userId User ID
     * @throws IllegalStateException if not approved
     */
    void checkApprovedStatus(Long userId);

    /**
     * Check if reseller owns the resource
     * @param userId User ID
     * @param resourceResellerId Resource's reseller ID
     * @throws IllegalAccessException if mismatch
     */
    void checkResourceOwnership(Long userId, Long resourceResellerId);
}

