package org.psint.beyosclothing.modules.resellers.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.resellers.dto.request.WithdrawalRequestRequest;
import org.psint.beyosclothing.modules.resellers.dto.response.WithdrawalListResponse;
import org.psint.beyosclothing.modules.resellers.dto.response.WithdrawalRequestResponse;
import org.psint.beyosclothing.modules.resellers.entity.*;
import org.psint.beyosclothing.modules.resellers.repository.ResellerBankAccountRepository;
import org.psint.beyosclothing.modules.resellers.repository.ResellerRepository;
import org.psint.beyosclothing.modules.resellers.repository.ResellerWithdrawalRequestRepository;
import org.psint.beyosclothing.modules.resellers.service.ResellerSecurityService;
import org.psint.beyosclothing.modules.resellers.service.ResellerService;
import org.psint.beyosclothing.modules.resellers.service.ResellerWalletService;
import org.psint.beyosclothing.modules.resellers.service.ResellerWithdrawalService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of Reseller Withdrawal Service
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional("resellerTransactionManager")
public class ResellerWithdrawalServiceImpl implements ResellerWithdrawalService {

    private final ResellerWithdrawalRequestRepository withdrawalRepository;
    private final ResellerBankAccountRepository bankAccountRepository;
    private final ResellerService resellerService;
    private final ResellerSecurityService securityService;
    private final ResellerWalletService walletService;
    private final ResellerRepository resellerRepository;
    private final org.psint.beyosclothing.modules.resellers.repository.ResellerWalletTransactionRepository transactionRepository;

    private static final BigDecimal MINIMUM_WITHDRAWAL_AMOUNT = BigDecimal.valueOf(1000);

    @Override
    public WithdrawalRequestResponse createWithdrawalRequest(Long resellerId, WithdrawalRequestRequest request) {
        // Lookup reseller by DB id to obtain the linked userId, then fetch reseller via service
        Reseller existing = resellerRepository.findById(resellerId)
                .orElseThrow(() -> new IllegalArgumentException("Reseller not found with id: " + resellerId));
        Long userId = existing.getUserId();
        Reseller reseller = resellerService.getResellerByUserId(userId);

        // Validate bank account
        ResellerBankAccount bankAccount = bankAccountRepository.findByUuid(request.getBankAccountUuid())
                .orElseThrow(() -> new IllegalArgumentException("Bank account not found"));

        if (!bankAccount.getResellerId().equals(reseller.getId())) {
            throw new IllegalArgumentException("Bank account does not belong to this reseller");
        }

        // Validate amount
        if (request.getAmount().compareTo(MINIMUM_WITHDRAWAL_AMOUNT) < 0) {
            throw new IllegalArgumentException("Minimum withdrawal amount is " + MINIMUM_WITHDRAWAL_AMOUNT);
        }

        BigDecimal currentBalance = walletService.getBalance(reseller.getId());
        if (request.getAmount().compareTo(currentBalance) > 0) {
            throw new IllegalArgumentException("Insufficient balance for withdrawal");
        }

        // Check no other pending withdrawal
        List<ResellerWithdrawalRequest> pendingWithdrawals = withdrawalRepository.findPendingByResellerId(reseller.getId());
        if (!pendingWithdrawals.isEmpty()) {
            throw new IllegalArgumentException("You already have a pending withdrawal request");
        }

        // Create withdrawal request (save first so we have an ID to reference from the wallet transaction)
        ResellerWithdrawalRequest withdrawal = ResellerWithdrawalRequest.builder()
                .resellerId(reseller.getId())
                .bankAccountId(bankAccount.getId())
                .amount(request.getAmount())
                .status(WithdrawalStatus.PENDING)
                .requestedDate(LocalDateTime.now())
                .balanceBefore(currentBalance)
                .balanceAfter(currentBalance.subtract(request.getAmount()))
                .build();

        withdrawal = withdrawalRepository.save(withdrawal);

        // Debit wallet and reference the withdrawal ID so the transaction has reference_withdrawal_id
        walletService.debitForWithdrawal(reseller.getId(), request.getAmount(), withdrawal.getId());

        // Publish event
        publishWithdrawalRequestedEvent(withdrawal);

        // Send emails
        sendAdminNotificationEmail(withdrawal);
        sendConfirmationEmail(reseller, withdrawal);

        log.info("Withdrawal request created: {} for reseller: {}", withdrawal.getUuid(), reseller.getUuid());

        return mapToWithdrawalResponse(withdrawal, bankAccount);
    }

    @Override
    public WithdrawalListResponse getWithdrawalRequests(Long resellerId, Pageable pageable, String status, LocalDateTime start, LocalDateTime end) {
        Reseller existing = resellerRepository.findById(resellerId)
                .orElseThrow(() -> new IllegalArgumentException("Reseller not found with id: " + resellerId));
        Long userId = existing.getUserId();
        Reseller reseller = resellerService.getResellerByUserId(userId);

        // Convert optional status string to enum (if provided)
        WithdrawalStatus statusEnum = null;
        if (status != null && !status.isBlank()) {
            try {
                statusEnum = WithdrawalStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Invalid status value: " + status);
            }
        }

        // Use repository method that supports optional status and date range filtering
        Page<ResellerWithdrawalRequest> withdrawalsPage =
                withdrawalRepository.findByResellerIdAndOptionalStatusAndRequestedDateBetween(reseller.getId(), statusEnum, start, end, pageable);

        List<WithdrawalListResponse.WithdrawalSummary> withdrawals = withdrawalsPage.getContent()
                .stream()
                .map(this::mapToWithdrawalSummary)
                .collect(Collectors.toList());

        return WithdrawalListResponse.builder()
                .withdrawals(withdrawals)
                .currentPage(pageable.getPageNumber())
                .totalPages(withdrawalsPage.getTotalPages())
                .totalWithdrawals(withdrawalsPage.getTotalElements())
                .pageSize(pageable.getPageSize())
                .build();
    }

    @Override
    public WithdrawalRequestResponse getWithdrawalByUuid(Long resellerId, String withdrawalUuid) {
        Reseller existing = resellerRepository.findById(resellerId)
                .orElseThrow(() -> new IllegalArgumentException("Reseller not found with id: " + resellerId));
        Long userId = existing.getUserId();
        Reseller reseller = resellerService.getResellerByUserId(userId);

        ResellerWithdrawalRequest withdrawal = withdrawalRepository.findByUuid(withdrawalUuid)
                .orElseThrow(() -> new IllegalArgumentException("Withdrawal request not found"));

        // Validate ownership
        if (!withdrawal.getResellerId().equals(reseller.getId())) {
            throw new IllegalArgumentException("Access denied");
        }

        ResellerBankAccount bankAccount = bankAccountRepository.findById(withdrawal.getBankAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Bank account not found"));

        return mapToWithdrawalResponse(withdrawal, bankAccount);
    }

    @Override
    public WithdrawalListResponse getPendingWithdrawals(Long resellerId) {
        Reseller existing = resellerRepository.findById(resellerId)
                .orElseThrow(() -> new IllegalArgumentException("Reseller not found with id: " + resellerId));
        Long userId = existing.getUserId();
        Reseller reseller = resellerService.getResellerByUserId(userId);

        List<ResellerWithdrawalRequest> pendingWithdrawals = withdrawalRepository.findPendingByResellerId(reseller.getId());

        List<WithdrawalListResponse.WithdrawalSummary> withdrawals = pendingWithdrawals.stream()
                .map(this::mapToWithdrawalSummary)
                .collect(Collectors.toList());

        return WithdrawalListResponse.builder()
                .withdrawals(withdrawals)
                .currentPage(0)
                .totalPages(1)
                .totalWithdrawals((long) withdrawals.size())
                .pageSize(withdrawals.size())
                .build();
    }

    @Override
    public void cancelWithdrawalRequest(Long resellerId, String withdrawalUuid) {
        Reseller existing = resellerRepository.findById(resellerId)
                .orElseThrow(() -> new IllegalArgumentException("Reseller not found with id: " + resellerId));
        Long userId = existing.getUserId();
        Reseller reseller = resellerService.getResellerByUserId(userId);

        ResellerWithdrawalRequest withdrawal = withdrawalRepository.findByUuid(withdrawalUuid)
                .orElseThrow(() -> new IllegalArgumentException("Withdrawal request not found"));

        // Validate ownership
        if (!withdrawal.getResellerId().equals(reseller.getId())) {
            throw new IllegalArgumentException("Access denied");
        }

        if (!withdrawal.isPending()) {
            throw new IllegalStateException("Only pending withdrawals can be cancelled");
        }

        // Reverse the debit (refund to wallet)
        walletService.reverseWithdrawal(reseller.getId(), withdrawal.getAmount(), withdrawal.getId());

        // mark original debit transaction as FAIL
        var debitTx = transactionRepository.findFirstByReferenceWithdrawalIdAndTypeOrderByDateCreatedDesc(withdrawal.getId(), org.psint.beyosclothing.modules.resellers.entity.TransactionType.WITHDRAWAL_DEBIT);
        if (debitTx != null) {
            debitTx.setStatus(WalletTransactionStatus.FAIL);
            transactionRepository.save(debitTx);
        }

        withdrawal.setStatus(WithdrawalStatus.PENDING); // Set to CANCELLED if you add that status
        withdrawalRepository.save(withdrawal);

        log.info("Withdrawal cancelled and refunded: {}", withdrawalUuid);
    }

    @Override
    public void approveWithdrawal(String withdrawalUuid, Long adminId, String transactionReference, String adminNotes) {
        ResellerWithdrawalRequest withdrawal = withdrawalRepository.findByUuid(withdrawalUuid)
                .orElseThrow(() -> new IllegalArgumentException("Withdrawal request not found"));

        if (!withdrawal.isPending()) {
            throw new IllegalStateException("Only pending withdrawals can be approved");
        }

        withdrawal.setStatus(WithdrawalStatus.APPROVED);
        withdrawal.setProcessedByAdminId(adminId);
        withdrawal.setProcessedDate(LocalDateTime.now());
        withdrawal.setTransactionReference(transactionReference);
        withdrawal.setAdminNotes(adminNotes);

        withdrawalRepository.save(withdrawal);

        // mark the debit transaction as SUCCESS
        var debitTx = transactionRepository.findFirstByReferenceWithdrawalIdAndTypeOrderByDateCreatedDesc(withdrawal.getId(), org.psint.beyosclothing.modules.resellers.entity.TransactionType.WITHDRAWAL_DEBIT);
        if (debitTx != null) {
            debitTx.setStatus(WalletTransactionStatus.SUCCESS);
            transactionRepository.save(debitTx);
        }

        // Publish event
        publishWithdrawalApprovedEvent(withdrawal);

        // Send email to reseller
        sendApprovalEmail(withdrawal);

        log.info("Withdrawal approved: {} by admin: {}", withdrawalUuid, adminId);
    }

    @Override
    public void rejectWithdrawal(String withdrawalUuid, Long adminId, String rejectionReason, String adminNotes) {
        ResellerWithdrawalRequest withdrawal = withdrawalRepository.findByUuid(withdrawalUuid)
                .orElseThrow(() -> new IllegalArgumentException("Withdrawal request not found"));

        if (!withdrawal.isPending()) {
            throw new IllegalStateException("Only pending withdrawals can be rejected");
        }

        withdrawal.setStatus(WithdrawalStatus.REJECTED);
        withdrawal.setProcessedByAdminId(adminId);
        withdrawal.setProcessedDate(LocalDateTime.now());
        withdrawal.setRejectionReason(rejectionReason);
        withdrawal.setAdminNotes(adminNotes);

        withdrawalRepository.save(withdrawal);

        // Reverse the debit (refund to wallet)
        walletService.reverseWithdrawal(withdrawal.getResellerId(), withdrawal.getAmount(), withdrawal.getId());

        // mark the original debit as FAIL
        var debitTx = transactionRepository.findFirstByReferenceWithdrawalIdAndTypeOrderByDateCreatedDesc(withdrawal.getId(), org.psint.beyosclothing.modules.resellers.entity.TransactionType.WITHDRAWAL_DEBIT);
        if (debitTx != null) {
            debitTx.setStatus(WalletTransactionStatus.FAIL);
            transactionRepository.save(debitTx);
        }

        // Publish event
        publishWithdrawalRejectedEvent(withdrawal);

        // Send email to reseller
        sendRejectionEmail(withdrawal, rejectionReason);

        log.info("Withdrawal rejected: {} by admin: {}", withdrawalUuid, adminId);
    }

    @Override
    public void markAsCompleted(String withdrawalUuid, Long adminId) {
        ResellerWithdrawalRequest withdrawal = withdrawalRepository.findByUuid(withdrawalUuid)
                .orElseThrow(() -> new IllegalArgumentException("Withdrawal request not found"));

        if (withdrawal.getStatus() != WithdrawalStatus.APPROVED) {
            throw new IllegalStateException("Only approved withdrawals can be marked as completed");
        }

        withdrawal.setStatus(WithdrawalStatus.COMPLETED);
        withdrawal.setCompletedDate(LocalDateTime.now());

        withdrawalRepository.save(withdrawal);

        // mark the debit transaction as SUCCESS (if not already)
        var debitTx = transactionRepository.findFirstByReferenceWithdrawalIdAndTypeOrderByDateCreatedDesc(withdrawal.getId(), org.psint.beyosclothing.modules.resellers.entity.TransactionType.WITHDRAWAL_DEBIT);
        if (debitTx != null) {
            debitTx.setStatus(WalletTransactionStatus.SUCCESS);
            transactionRepository.save(debitTx);
        }

        // Send email
        sendCompletionEmail(withdrawal);

        log.info("Withdrawal marked as completed: {} by admin: {}", withdrawalUuid, adminId);
    }

    @Override
    public void markAsFailed(String withdrawalUuid, Long adminId, String failureReason) {
        ResellerWithdrawalRequest withdrawal = withdrawalRepository.findByUuid(withdrawalUuid)
                .orElseThrow(() -> new IllegalArgumentException("Withdrawal request not found"));

        withdrawal.setStatus(WithdrawalStatus.FAILED);
        withdrawal.setAdminNotes(failureReason);

        withdrawalRepository.save(withdrawal);

        // Reverse the debit (refund to wallet)
        walletService.reverseWithdrawal(withdrawal.getResellerId(), withdrawal.getAmount(), withdrawal.getId());

        // mark original debit as FAIL
        var debitTx = transactionRepository.findFirstByReferenceWithdrawalIdAndTypeOrderByDateCreatedDesc(withdrawal.getId(), org.psint.beyosclothing.modules.resellers.entity.TransactionType.WITHDRAWAL_DEBIT);
        if (debitTx != null) {
            debitTx.setStatus(org.psint.beyosclothing.modules.resellers.entity.WalletTransactionStatus.FAIL);
            transactionRepository.save(debitTx);
        }

        // Send email
        sendFailureEmail(withdrawal, failureReason);

        log.info("Withdrawal marked as failed: {} by admin: {}", withdrawalUuid, adminId);
    }

    // Helper methods
    private void publishWithdrawalRequestedEvent(ResellerWithdrawalRequest withdrawal) {
        log.info("Publishing WithdrawalRequestedEvent for: {}", withdrawal.getUuid());
        // TODO: Publish to RabbitMQ
    }

    private void publishWithdrawalApprovedEvent(ResellerWithdrawalRequest withdrawal) {
        log.info("Publishing WithdrawalApprovedEvent for: {}", withdrawal.getUuid());
        // TODO: Publish to RabbitMQ
    }

    private void publishWithdrawalRejectedEvent(ResellerWithdrawalRequest withdrawal) {
        log.info("Publishing WithdrawalRejectedEvent for: {}", withdrawal.getUuid());
        // TODO: Publish to RabbitMQ
    }

    private void sendAdminNotificationEmail(ResellerWithdrawalRequest withdrawal) {
        log.info("Sending admin notification for withdrawal: {}", withdrawal.getUuid());
        // TODO: Send email
    }

    private void sendConfirmationEmail(Reseller reseller, ResellerWithdrawalRequest withdrawal) {
        log.info("Sending withdrawal confirmation email to: {}", reseller.getEmail());
        // TODO: Send email
    }

    private void sendApprovalEmail(ResellerWithdrawalRequest withdrawal) {
        log.info("Sending withdrawal approval email");
        // TODO: Send email
    }

    private void sendRejectionEmail(ResellerWithdrawalRequest withdrawal, String reason) {
        log.info("Sending withdrawal rejection email");
        // TODO: Send email
    }

    private void sendCompletionEmail(ResellerWithdrawalRequest withdrawal) {
        log.info("Sending withdrawal completion email");
        // TODO: Send email
    }

    private void sendFailureEmail(ResellerWithdrawalRequest withdrawal, String reason) {
        log.info("Sending withdrawal failure email");
        // TODO: Send email
    }

    private WithdrawalRequestResponse mapToWithdrawalResponse(ResellerWithdrawalRequest withdrawal,
                                                              ResellerBankAccount bankAccount) {
        return WithdrawalRequestResponse.builder()
                .uuid(withdrawal.getUuid())
                .amount(withdrawal.getAmount())
                .status(withdrawal.getStatus().name())
                .bankAccount(WithdrawalRequestResponse.BankAccountInfo.builder()
                        .bankName(bankAccount.getBankName())
                        .maskedAccountNumber(bankAccount.getMaskedAccountNumber())
                        .accountHolderName(bankAccount.getAccountHolderName())
                        .build())
                .requestedDate(withdrawal.getRequestedDate())
                .processedDate(withdrawal.getProcessedDate())
                .completedDate(withdrawal.getCompletedDate())
                .adminNotes(withdrawal.getAdminNotes())
                .rejectionReason(withdrawal.getRejectionReason())
                .transactionReference(withdrawal.getTransactionReference())
                .balanceAfter(withdrawal.getBalanceAfter())
                .build();
    }

    private WithdrawalListResponse.WithdrawalSummary mapToWithdrawalSummary(ResellerWithdrawalRequest withdrawal) {
        ResellerBankAccount bankAccount = bankAccountRepository.findById(withdrawal.getBankAccountId())
                .orElse(null);

        return WithdrawalListResponse.WithdrawalSummary.builder()
                .uuid(withdrawal.getUuid())
                .amount(withdrawal.getAmount())
                .status(withdrawal.getStatus().name())
                .requestedDate(withdrawal.getRequestedDate())
                .bankName(bankAccount != null ? bankAccount.getBankName() : "Unknown")
                .build();
    }
}

