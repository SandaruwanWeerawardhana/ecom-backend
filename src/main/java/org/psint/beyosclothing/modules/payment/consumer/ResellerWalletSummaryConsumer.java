package org.psint.beyosclothing.modules.payment.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.resellers.entity.ResellerWalletTransaction;
import org.psint.beyosclothing.modules.resellers.entity.TransactionType;
import org.psint.beyosclothing.modules.resellers.repository.ResellerWalletTransactionRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Consumer for Reseller Wallet Summary Lookup
 * Handles RPC requests from Reseller module to fetch wallet earnings and withdrawals
 *
 * Request: { "requestType": "GET_RESELLER_WALLET_SUMMARY", "resellerId": Long }
 * Response: { "success": boolean, "totalEarnings": BigDecimal, "totalWithdrawn": BigDecimal,
 *             "recentTransactions": List<Map> }
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ResellerWalletSummaryConsumer {

    private final ResellerWalletTransactionRepository walletTransactionRepository;

    @RabbitListener(
            queues = "${app.rabbitmq.queue.reseller-wallet-summary-lookup-request:reseller.wallet.summary.lookup.request}",
            containerFactory = "paymentRabbitListenerContainerFactory"
    )
    @Transactional("paymentTransactionManager")
    public Map<String, Object> handleWalletSummaryLookup(Map<String, Object> request) {
        log.info("=== RESELLER WALLET SUMMARY LOOKUP CONSUMER TRIGGERED ===");

        try {
            Long resellerId = null;
            Object resellerIdObj = request.get("resellerId");
            if (resellerIdObj instanceof Number) {
                resellerId = ((Number) resellerIdObj).longValue();
            }

            if (resellerId == null) {
                log.warn("⚠️ Missing resellerId in wallet summary lookup request");
                return buildErrorResponse("Missing resellerId in request");
            }

            log.info("Fetching wallet summary for resellerId: {}", resellerId);

            // Calculate total earnings (sum of all credit transactions)
            BigDecimal totalEarnings = calculateTotalEarnings(resellerId);

            // Calculate total withdrawn (sum of all debit transactions)
            BigDecimal totalWithdrawn = calculateTotalWithdrawn(resellerId);

            // Fetch recent transactions (latest 10)
            List<Map<String, Object>> recentTransactions = fetchRecentTransactions(resellerId, 10);

            log.info("✅ Wallet summary retrieved - Total Earnings: {}, Total Withdrawn: {}",
                    totalEarnings, totalWithdrawn);

            return buildSuccessResponse(totalEarnings, totalWithdrawn, recentTransactions);

        } catch (Exception e) {
            log.error("❌ Error in reseller wallet summary lookup consumer: {}", e.getMessage(), e);
            return buildErrorResponse("Error fetching wallet summary: " + e.getMessage());
        }
    }

    /**
     * Calculate total earnings for a reseller (sum of all credit transactions)
     * Credit types: SALE_PROFIT, WITHDRAWAL_REVERSED, ADMIN_CREDIT, ORDER_REFUND
     */
    private BigDecimal calculateTotalEarnings(Long resellerId) {
        BigDecimal saleProfits = walletTransactionRepository.calculateTotalByResellerIdAndType(
                resellerId, TransactionType.SALE_PROFIT);
        BigDecimal withdrawalReversed = walletTransactionRepository.calculateTotalByResellerIdAndType(
                resellerId, TransactionType.WITHDRAWAL_REVERSED);
        BigDecimal adminCredit = walletTransactionRepository.calculateTotalByResellerIdAndType(
                resellerId, TransactionType.ADMIN_CREDIT);
        BigDecimal orderRefund = walletTransactionRepository.calculateTotalByResellerIdAndType(
                resellerId, TransactionType.ORDER_REFUND);

        BigDecimal totalEarnings = saleProfits.add(withdrawalReversed)
                .add(adminCredit)
                .add(orderRefund);

        log.debug("Total earnings breakdown - SaleProfits: {}, WithdrawalReversed: {}, AdminCredit: {}, OrderRefund: {}",
                saleProfits, withdrawalReversed, adminCredit, orderRefund);

        return totalEarnings;
    }

    /**
     * Calculate total withdrawn for a reseller (sum of all debit transactions)
     * Debit types: WITHDRAWAL_DEBIT, ADMIN_DEBIT
     */
    private BigDecimal calculateTotalWithdrawn(Long resellerId) {
        BigDecimal withdrawalDebit = walletTransactionRepository.calculateTotalByResellerIdAndType(
                resellerId, TransactionType.WITHDRAWAL_DEBIT);
        BigDecimal adminDebit = walletTransactionRepository.calculateTotalByResellerIdAndType(
                resellerId, TransactionType.ADMIN_DEBIT);

        BigDecimal totalWithdrawn = withdrawalDebit.add(adminDebit);

        log.debug("Total withdrawn breakdown - WithdrawalDebit: {}, AdminDebit: {}",
                withdrawalDebit, adminDebit);

        return totalWithdrawn;
    }

    /**
     * Fetch recent wallet transactions for a reseller
     */
    private List<Map<String, Object>> fetchRecentTransactions(Long resellerId, int limit) {
        List<Map<String, Object>> transactions = new ArrayList<>();

        try {
            Pageable pageable = PageRequest.of(0, limit);
            var recentTransactionsPage = walletTransactionRepository
                    .findByResellerIdOrderByDateCreatedDesc(resellerId, pageable);

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            for (ResellerWalletTransaction transaction : recentTransactionsPage.getContent()) {
                Map<String, Object> transactionMap = new HashMap<>();
                transactionMap.put("uuid", transaction.getUuid());
                transactionMap.put("type", transaction.isCredit() ? "Credit" : "Debit");
                transactionMap.put("description", getTransactionDescription(transaction));
                transactionMap.put("amount", transaction.getAmount());
                transactionMap.put("date", transaction.getDateCreated().format(formatter));
                transactionMap.put("status", transaction.getStatus().toString());

                transactions.add(transactionMap);
            }

            log.debug("Fetched {} recent transactions for resellerId: {}", transactions.size(), resellerId);

        } catch (Exception e) {
            log.error("Error fetching recent transactions for resellerId: {}", resellerId, e);
        }

        return transactions;
    }

    /**
     * Generate human-readable transaction description
     */
    private String getTransactionDescription(ResellerWalletTransaction transaction) {
        return switch (transaction.getType()) {
            case SALE_PROFIT -> "Order #" + transaction.getReferenceOrderId() + " Payment";
            case WITHDRAWAL_DEBIT -> "Withdrawal #" + transaction.getReferenceWithdrawalId();
            case WITHDRAWAL_REVERSED -> "Withdrawal Reversal #" + transaction.getReferenceWithdrawalId();
            case ADMIN_CREDIT -> "Admin Credit";
            case ADMIN_DEBIT -> "Admin Debit";
            case ORDER_REFUND -> "Refund for Order #" + transaction.getReferenceOrderId();
        };
    }

    private Map<String, Object> buildSuccessResponse(BigDecimal totalEarnings, BigDecimal totalWithdrawn,
                                                      List<Map<String, Object>> recentTransactions) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("totalEarnings", totalEarnings);
        response.put("totalWithdrawn", totalWithdrawn);
        response.put("recentTransactions", recentTransactions);
        return response;
    }

    private Map<String, Object> buildErrorResponse(String error) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", error);
        return response;
    }
}
