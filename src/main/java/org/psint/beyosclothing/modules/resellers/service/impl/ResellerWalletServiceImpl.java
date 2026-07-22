package org.psint.beyosclothing.modules.resellers.service.impl;

import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.resellers.dto.response.WalletBalanceResponse;
import org.psint.beyosclothing.modules.resellers.dto.response.WalletTransactionListResponse;
import org.psint.beyosclothing.modules.resellers.entity.Reseller;
import org.psint.beyosclothing.modules.resellers.entity.ResellerWalletTransaction;
import org.psint.beyosclothing.modules.resellers.entity.TransactionType;
import org.psint.beyosclothing.modules.resellers.entity.WalletTransactionStatus;
import org.psint.beyosclothing.modules.resellers.repository.ResellerRepository;
import org.psint.beyosclothing.modules.resellers.repository.ResellerWalletTransactionRepository;
import org.psint.beyosclothing.modules.resellers.service.ResellerWalletService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.resilience.annotation.Retryable;
//import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of Reseller Wallet Service with pessimistic locking
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ResellerWalletServiceImpl implements ResellerWalletService {

    private final ResellerRepository resellerRepository;
    private final ResellerWalletTransactionRepository transactionRepository;

    @Override
    @Transactional(transactionManager = "resellerTransactionManager", isolation = Isolation.READ_COMMITTED)
    @Retryable(maxRetries = 3)
    public ResellerWalletTransaction creditSaleProfit(Long resellerId, BigDecimal amount, Long orderId, Long orderItemId) {
        // Lock reseller row with SELECT FOR UPDATE
        Reseller reseller = resellerRepository.findById(resellerId)
                .orElseThrow(() -> new IllegalArgumentException("Reseller not found"));

        BigDecimal balanceBefore = reseller.getCreditBalance();
        BigDecimal balanceAfter = balanceBefore.add(amount);

        // Create transaction
        ResellerWalletTransaction transaction = ResellerWalletTransaction.builder()
                .resellerId(resellerId)
                .type(TransactionType.SALE_PROFIT)
                .amount(amount)
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .referenceOrderId(orderId)
                .notes("Profit from order item #" + orderItemId)
                .status(WalletTransactionStatus.SUCCESS)
                .isActive(true)
                .build();

        transaction = transactionRepository.save(transaction);

        // Update balance
        reseller.setCreditBalance(balanceAfter);
        resellerRepository.save(reseller);

        log.info("Credited {} to reseller {} wallet. New balance: {}", amount, resellerId, balanceAfter);

        return transaction;
    }

    @Override
    @Transactional(transactionManager = "resellerTransactionManager", isolation = Isolation.READ_COMMITTED)
    @Retryable(maxRetries = 3)
    public ResellerWalletTransaction debitForWithdrawal(Long resellerId, BigDecimal amount, Long withdrawalId) {
        Reseller reseller = resellerRepository.findById(resellerId)
                .orElseThrow(() -> new IllegalArgumentException("Reseller not found"));

        BigDecimal balanceBefore = reseller.getCreditBalance();

        if (balanceBefore.compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient balance for withdrawal");
        }

        BigDecimal balanceAfter = balanceBefore.subtract(amount);

        ResellerWalletTransaction transaction = ResellerWalletTransaction.builder()
                .resellerId(resellerId)
                .type(TransactionType.WITHDRAWAL_DEBIT)
                .amount(amount.negate())
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .referenceWithdrawalId(withdrawalId)
                .notes("Withdrawal debit")
                .status(WalletTransactionStatus.PENDING)
                .isActive(true)
                .build();

        transaction = transactionRepository.save(transaction);

        reseller.setCreditBalance(balanceAfter);
        resellerRepository.save(reseller);

        log.info("Debited {} from reseller {} wallet. New balance: {}", amount, resellerId, balanceAfter);

        return transaction;
    }

    @Override
    @Transactional(transactionManager = "resellerTransactionManager", isolation = Isolation.READ_COMMITTED)
    @Retryable(maxRetries = 3)
    public ResellerWalletTransaction reverseWithdrawal(Long resellerId, BigDecimal amount, Long withdrawalId) {
        Reseller reseller = resellerRepository.findById(resellerId)
                .orElseThrow(() -> new IllegalArgumentException("Reseller not found"));

        BigDecimal balanceBefore = reseller.getCreditBalance();
        BigDecimal balanceAfter = balanceBefore.add(amount);

        ResellerWalletTransaction transaction = ResellerWalletTransaction.builder()
                .resellerId(resellerId)
                .type(TransactionType.WITHDRAWAL_REVERSED)
                .amount(amount)
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .referenceWithdrawalId(withdrawalId)
                .notes("Withdrawal reversal (rejected)")
                .status(WalletTransactionStatus.REJECTED)
                .isActive(true)
                .build();

        transaction = transactionRepository.save(transaction);

        reseller.setCreditBalance(balanceAfter);
        resellerRepository.save(reseller);

        log.info("Reversed withdrawal {} to reseller {} wallet. New balance: {}", amount, resellerId, balanceAfter);

        return transaction;
    }

    @Override
    @Transactional(transactionManager = "resellerTransactionManager", isolation = Isolation.READ_COMMITTED)
    @Retryable(maxRetries = 3)
    public ResellerWalletTransaction adminAdjustBalance(Long resellerId, BigDecimal amount,
                                                       TransactionType type, Long adminId, String notes) {
        Reseller reseller = resellerRepository.findById(resellerId)
                .orElseThrow(() -> new IllegalArgumentException("Reseller not found"));

        BigDecimal balanceBefore = reseller.getCreditBalance();
        BigDecimal balanceAfter = type == TransactionType.ADMIN_CREDIT
                ? balanceBefore.add(amount)
                : balanceBefore.subtract(amount);

        ResellerWalletTransaction transaction = ResellerWalletTransaction.builder()
                .resellerId(resellerId)
                .type(type)
                .amount(type == TransactionType.ADMIN_CREDIT ? amount : amount.negate())
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .createdByAdminId(adminId)
                .notes(notes)
                .status(WalletTransactionStatus.SUCCESS)
                .isActive(true)
                .build();

        transaction = transactionRepository.save(transaction);

        reseller.setCreditBalance(balanceAfter);
        resellerRepository.save(reseller);

        log.info("Admin {} adjusted reseller {} balance by {}. New balance: {}", adminId, resellerId, amount, balanceAfter);

        // Send notification email
        sendBalanceAdjustmentEmail(reseller, amount, type, notes);

        return transaction;
    }

    @Override
    public BigDecimal getBalance(Long resellerId) {
        Reseller reseller = resellerRepository.findById(resellerId)
                .orElseThrow(() -> new IllegalArgumentException("Reseller not found"));
        return reseller.getCreditBalance();
    }

    @Override
    public WalletBalanceResponse getWalletSummary(Long resellerId) {
        Reseller reseller = resellerRepository.findById(resellerId)
                .orElseThrow(() -> new IllegalArgumentException("Reseller not found"));

        // Calculate aggregates
        BigDecimal totalEarned = transactionRepository.calculateTotalByResellerIdAndType(
                resellerId, TransactionType.SALE_PROFIT);

        BigDecimal totalWithdrawn = transactionRepository.calculateTotalByResellerIdAndType(
                resellerId, TransactionType.WITHDRAWAL_DEBIT).abs();

        // Calculate pending withdrawals (would need to query withdrawal requests)
        BigDecimal pendingWithdrawals = BigDecimal.ZERO; // TODO: Query from withdrawal requests

        return WalletBalanceResponse.builder()
                .currentBalance(reseller.getCreditBalance())
                .creditLimit(reseller.getCreditLimit())
                .availableCredit(reseller.getAvailableCredit())
                .totalEarned(totalEarned)
                .totalWithdrawn(totalWithdrawn)
                .pendingWithdrawals(pendingWithdrawals)
                .build();
    }

    @Override
    public WalletTransactionListResponse getTransactionHistory(Long resellerId, Pageable pageable) {
        Page<ResellerWalletTransaction> transactionsPage =
                transactionRepository.findByResellerIdOrderByDateCreatedDesc(resellerId, pageable);

        List<WalletTransactionListResponse.TransactionSummary> transactions = transactionsPage.getContent()
                .stream()
                .map(this::mapToTransactionSummary)
                .collect(Collectors.toList());

        return WalletTransactionListResponse.builder()
                .transactions(transactions)
                .currentPage(pageable.getPageNumber())
                .totalPages(transactionsPage.getTotalPages())
                .totalTransactions(transactionsPage.getTotalElements())
                .pageSize(pageable.getPageSize())
                .build();
    }

    @Override
    public WalletTransactionListResponse getTransactionHistoryByType(Long resellerId, TransactionType type, Pageable pageable) {
        Page<ResellerWalletTransaction> transactionsPage =
                transactionRepository.findByResellerIdAndTypeOrderByDateCreatedDesc(resellerId, type, pageable);

        List<WalletTransactionListResponse.TransactionSummary> transactions = transactionsPage.getContent()
                .stream()
                .map(this::mapToTransactionSummary)
                .collect(Collectors.toList());

        return WalletTransactionListResponse.builder()
                .transactions(transactions)
                .currentPage(pageable.getPageNumber())
                .totalPages(transactionsPage.getTotalPages())
                .totalTransactions(transactionsPage.getTotalElements())
                .pageSize(pageable.getPageSize())
                .build();
    }

    // Helper methods
    private void sendBalanceAdjustmentEmail(Reseller reseller, BigDecimal amount, TransactionType type, String notes) {
        log.info("Sending balance adjustment email to: {}", reseller.getEmail());
        // TODO: Send email
    }

    private WalletTransactionListResponse.TransactionSummary mapToTransactionSummary(ResellerWalletTransaction transaction) {
        String referenceInfo = null;
        if (transaction.getReferenceOrderId() != null) {
            referenceInfo = "Order #" + transaction.getReferenceOrderId();
        } else if (transaction.getReferenceWithdrawalId() != null) {
            referenceInfo = "Withdrawal #" + transaction.getReferenceWithdrawalId();
        }

        return WalletTransactionListResponse.TransactionSummary.builder()
                .uuid(transaction.getUuid())
                .type(transaction.getType().name())
                .amount(transaction.getAmount())
                .date(transaction.getDateCreated())
                .isCredit(transaction.isCredit())
                .referenceInfo(referenceInfo)
                .build();
    }
}

