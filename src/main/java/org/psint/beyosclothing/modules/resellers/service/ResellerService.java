package org.psint.beyosclothing.modules.resellers.service;

import org.psint.beyosclothing.modules.resellers.dto.request.ResellerRegistrationRequest;
import org.psint.beyosclothing.modules.resellers.dto.request.ResellerUpdateRequest;
import org.psint.beyosclothing.modules.resellers.dto.response.ResellerProfileResponse;
import org.psint.beyosclothing.modules.resellers.entity.Reseller;

/**
 * Service interface for Reseller operations
 */
public interface ResellerService {

    /**
     * Register a new reseller
     * @param request Registration details
     * @return Profile response with UUID
     */
    ResellerProfileResponse register(ResellerRegistrationRequest request);

    /**
     * Get reseller profile by user ID
     * @param userId User ID from JWT token
     * @return Profile response
     */
    ResellerProfileResponse getProfile(Long userId);

    /**
     * Update reseller profile
     * @param userId User ID
     * @param request Update details
     * @return Updated profile
     */
    ResellerProfileResponse updateProfile(Long userId, ResellerUpdateRequest request);

    /**
     * Get reseller status
     * @param userId User ID
     * @return Status message
     */
    String getStatus(Long userId);

    /**
     * Get reseller status by email
     * @param email Reseller email
     * @return Status message
     */
    String getStatusByEmail(String email);

    /**
     * Get reseller profile by email
     * @param email Reseller email
     * @return Profile response
     */
    ResellerProfileResponse getProfileByEmail(String email);

    /**
     * Get reseller entity by user ID
     * @param userId User ID
     * @return Reseller entity
     */
    Reseller getResellerByUserId(Long userId);

    /**
     * Get reseller entity by UUID
     * @param uuid Reseller UUID
     * @return Reseller entity
     */
    Reseller getResellerByUuid(String uuid);

    /**
     * Approve reseller
     * @param resellerUuid Reseller UUID
     * @param allowPriceOverride Allow price override
     * @param minMarkupPct Minimum markup percentage
     * @param maxMarkupPct Maximum markup percentage
     * @param creditLimit Credit limit
     * @param adminId Admin ID who approved
     */
    void approveReseller(String resellerUuid, Boolean allowPriceOverride,
                        java.math.BigDecimal minMarkupPct,
                        java.math.BigDecimal maxMarkupPct,
                        java.math.BigDecimal creditLimit,
                        Long adminId);

    /**
     * Reject reseller
     * @param resellerUuid Reseller UUID
     * @param rejectionReason Reason for rejection
     * @param adminId Admin ID who rejected
     */
    void rejectReseller(String resellerUuid, String rejectionReason, Long adminId);

    /**
     * Suspend reseller
     * @param resellerUuid Reseller UUID
     * @param suspensionReason Reason for suspension
     * @param adminId Admin ID who suspended
     */
    void suspendReseller(String resellerUuid, String suspensionReason, Long adminId);

    /**
     * Reactivate suspended reseller
     * @param resellerUuid Reseller UUID
     * @param adminId Admin ID who reactivated
     */
    void reactivateReseller(String resellerUuid, Long adminId);

    /**
     * Update reseller settings
     * @param resellerUuid Reseller UUID
     * @param allowPriceOverride Allow price override
     * @param minMarkupPct Minimum markup percentage
     * @param maxMarkupPct Maximum markup percentage
     * @param creditLimit Credit limit
     * @param adminId Admin ID
     */
    void updateSettings(String resellerUuid, Boolean allowPriceOverride,
                       java.math.BigDecimal minMarkupPct,
                       java.math.BigDecimal maxMarkupPct,
                       java.math.BigDecimal creditLimit,
                       Long adminId);

    /**
     * Change password for the given userId. Implementations should verify the current password,
     * validate the new password against policy and history, and perform the update via the
     * auth service or local user store.
     * @param uuId authenticated user id
     * @param currentPassword current password provided by the user
     * @param newPassword new desired password
     */
    void changePassword(String uuId, String currentPassword, String newPassword, String confirmPassword);

    void deleteReseller(String uuid);
}
