package org.psint.beyosclothing.modules.resellers.service;

import org.psint.beyosclothing.modules.resellers.dto.response.AdminResellerDetailResponse;
import org.psint.beyosclothing.modules.resellers.dto.response.AdminResellerListResponse;
import org.psint.beyosclothing.modules.resellers.dto.response.WithdrawalListResponse;
import org.springframework.data.domain.Pageable;

/**
 * Service interface for Admin Reseller Management
 */
public interface AdminResellerService {

    /**
     * Get paginated list of resellers
     * @param pageable Pagination parameters
     * @param status Filter by status (optional)
     * @return Paginated reseller list
     */
    AdminResellerListResponse getAllResellers(Pageable pageable, String status);

    /**
     * Get detailed reseller information
     * @param resellerUuid Reseller UUID
     * @return Detailed reseller response
     */
    AdminResellerDetailResponse getResellerDetails(String resellerUuid);

    /**
     * Get all pending withdrawal requests paginated.
     */
    WithdrawalListResponse getWithdrawals(Pageable pageable);

    /**
     * Get ALL withdrawal requests paginated (all statuses).
     */
    WithdrawalListResponse getAllWithdrawals(Pageable pageable);

    /**
     * @param withdrawalUuid UUID of the withdrawal request
     * @param newStatus      APPROVED or REJECTED (case-insensitive)
     * @param adminId        ID of the admin performing the action
     * @param notes          Optional admin notes / rejection reason
     */
    void updateWithdrawalStatus(String withdrawalUuid, String newStatus, Long adminId, String notes);
}
