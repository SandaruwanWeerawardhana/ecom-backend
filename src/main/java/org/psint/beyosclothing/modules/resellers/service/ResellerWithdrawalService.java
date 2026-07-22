package org.psint.beyosclothing.modules.resellers.service;

import org.psint.beyosclothing.modules.resellers.dto.request.WithdrawalRequestRequest;
import org.psint.beyosclothing.modules.resellers.dto.response.WithdrawalListResponse;
import org.psint.beyosclothing.modules.resellers.dto.response.WithdrawalRequestResponse;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

/**
 * Service interface for Reseller Withdrawal operations
 */
public interface ResellerWithdrawalService {

    /**
     * Create withdrawal request
     * @param resellerId Reseller ID
     * @param request Withdrawal request
     * @return Withdrawal response
     */
    WithdrawalRequestResponse createWithdrawalRequest(Long resellerId, WithdrawalRequestRequest request);

    /**
     * Get withdrawal requests for reseller with optional date range
     * @param resellerId Reseller ID
     * @param pageable Pagination
     * @param status optional status string for filtering
     * @param start optional start datetime (inclusive)
     * @param end optional end datetime (inclusive)
     * @return Paginated withdrawal list
     */
    WithdrawalListResponse getWithdrawalRequests(Long resellerId, Pageable pageable, String status, LocalDateTime start, LocalDateTime end);

    /**
     * Get withdrawal by UUID
     * @param resellerId Reseller ID
     * @param withdrawalUuid Withdrawal UUID
     * @return Withdrawal details
     */
    WithdrawalRequestResponse getWithdrawalByUuid(Long resellerId, String withdrawalUuid);

    /**
     * Get pending withdrawals
     * @param resellerId Reseller ID
     * @return List of pending withdrawals
     */
    WithdrawalListResponse getPendingWithdrawals(Long resellerId);

    /**
     * Cancel withdrawal request (only if PENDING)
     * @param resellerId Reseller ID
     * @param withdrawalUuid Withdrawal UUID
     */
    void cancelWithdrawalRequest(Long resellerId, String withdrawalUuid);

    /**
     * Admin approve withdrawal
     * @param withdrawalUuid Withdrawal UUID
     * @param adminId Admin ID
     * @param transactionReference Bank transfer reference
     * @param adminNotes Admin notes
     */
    void approveWithdrawal(String withdrawalUuid, Long adminId, String transactionReference, String adminNotes);

    /**
     * Admin reject withdrawal
     * @param withdrawalUuid Withdrawal UUID
     * @param adminId Admin ID
     * @param rejectionReason Reason for rejection
     * @param adminNotes Admin notes
     */
    void rejectWithdrawal(String withdrawalUuid, Long adminId, String rejectionReason, String adminNotes);

    /**
     * Mark withdrawal as completed
     * @param withdrawalUuid Withdrawal UUID
     * @param adminId Admin ID
     */
    void markAsCompleted(String withdrawalUuid, Long adminId);

    /**
     * Mark withdrawal as failed
     * @param withdrawalUuid Withdrawal UUID
     * @param adminId Admin ID
     * @param failureReason Failure reason
     */
    void markAsFailed(String withdrawalUuid, Long adminId, String failureReason);
}

