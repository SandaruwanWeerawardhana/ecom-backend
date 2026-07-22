package org.psint.beyosclothing.modules.resellers.service;

import org.psint.beyosclothing.modules.resellers.dto.response.WalletBalanceResponse;
import org.psint.beyosclothing.modules.resellers.dto.response.WalletTransactionListResponse;
import org.psint.beyosclothing.modules.resellers.entity.ResellerWalletTransaction;
import org.psint.beyosclothing.modules.resellers.entity.TransactionType;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

/**
 * Service interface for Reseller Wallet operations
 */
public interface ResellerWalletService {

    /**
     * Credit sale profit to wallet
     * @param resellerId Reseller ID
     * @param amount Amount to credit
     * @param orderId Order ID reference
     * @param orderItemId Order item ID reference
     * @return Transaction
     */
    ResellerWalletTransaction creditSaleProfit(Long resellerId, BigDecimal amount, Long orderId, Long orderItemId);

    /**
     * Debit for withdrawal
     * @param resellerId Reseller ID
     * @param amount Amount to debit
     * @param withdrawalId Withdrawal request ID
     * @return Transaction
     */
    ResellerWalletTransaction debitForWithdrawal(Long resellerId, BigDecimal amount, Long withdrawalId);

    /**
     * Reverse withdrawal (refund)
     * @param resellerId Reseller ID
     * @param amount Amount to reverse
     * @param withdrawalId Withdrawal request ID
     * @return Transaction
     */
    ResellerWalletTransaction reverseWithdrawal(Long resellerId, BigDecimal amount, Long withdrawalId);

    /**
     * Admin adjust balance
     * @param resellerId Reseller ID
     * @param amount Amount (positive or negative)
     * @param type Transaction type (ADMIN_CREDIT or ADMIN_DEBIT)
     * @param adminId Admin user ID
     * @param notes Reason for adjustment
     * @return Transaction
     */
    ResellerWalletTransaction adminAdjustBalance(Long resellerId, BigDecimal amount,
                                                 TransactionType type, Long adminId, String notes);

    /**
     * Get current balance
     * @param resellerId Reseller ID
     * @return Current balance
     */
    BigDecimal getBalance(Long resellerId);

    /**
     * Get wallet summary
     * @param resellerId Reseller ID
     * @return Wallet summary
     */
    WalletBalanceResponse getWalletSummary(Long resellerId);

    /**
     * Get transaction history
     * @param resellerId Reseller ID
     * @param pageable Pagination
     * @return Transaction list
     */
    WalletTransactionListResponse getTransactionHistory(Long resellerId, Pageable pageable);

    /**
     * Get transaction history by type
     * @param resellerId Reseller ID
     * @param type Transaction type
     * @param pageable Pagination
     * @return Transaction list
     */
    WalletTransactionListResponse getTransactionHistoryByType(Long resellerId, TransactionType type, Pageable pageable);
}

