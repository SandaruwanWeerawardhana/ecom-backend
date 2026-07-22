package org.psint.beyosclothing.modules.resellers.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.resellers.dto.response.AdminResellerDetailResponse;
import org.psint.beyosclothing.modules.resellers.dto.response.AdminResellerListResponse;
import org.psint.beyosclothing.modules.resellers.dto.response.WithdrawalListResponse;
import org.psint.beyosclothing.modules.resellers.entity.*;
import org.psint.beyosclothing.modules.resellers.repository.ResellerRepository;
import org.psint.beyosclothing.modules.resellers.repository.ResellerWalletTransactionRepository;
import org.psint.beyosclothing.modules.resellers.repository.ResellerWithdrawalRequestRepository;
import org.psint.beyosclothing.modules.resellers.repository.ResellerBankAccountRepository;
import org.psint.beyosclothing.modules.resellers.service.AdminResellerService;
import org.psint.beyosclothing.modules.resellers.service.ResellerWalletService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of Admin Reseller Service
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminResellerServiceImpl implements AdminResellerService {

    private final ResellerRepository resellerRepository;
    private final ResellerWithdrawalRequestRepository withdrawalRepository;
    private final ResellerBankAccountRepository bankAccountRepository;
    private final ResellerWalletService walletService;
    private final ResellerWalletTransactionRepository transactionRepository;

    @Override
    public AdminResellerListResponse getAllResellers(Pageable pageable, String status) {
        log.info("Admin fetching resellers - Status filter: {}, Page: {}", status, pageable.getPageNumber());

        Page<Reseller> resellerPage;

        if (status != null && !status.isBlank()) {
            try {
                ResellerStatus resellerStatus = ResellerStatus.valueOf(status.toUpperCase());
                resellerPage = resellerRepository.findByStatus(resellerStatus, pageable);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid status filter: {}", status);
                resellerPage = resellerRepository.findAll(pageable);
            }
        } else {
            resellerPage = resellerRepository.findAll(pageable);
        }

        List<AdminResellerListResponse.ResellerSummary> resellers = resellerPage.getContent()
                .stream().filter(reseller -> Boolean.TRUE.equals(reseller.getIsActive()))
                .map(this::mapToSummary)
                .collect(Collectors.toList());

        return AdminResellerListResponse.builder()
                .resellers(resellers)
                .currentPage(resellerPage.getNumber())
                .totalPages(resellerPage.getTotalPages())
                .totalResellers(resellerPage.getTotalElements())
                .pageSize(resellerPage.getSize())
                .build();
    }

    @Override
    public AdminResellerDetailResponse getResellerDetails(String resellerUuid) {
        log.info("Admin fetching reseller details for: {}", resellerUuid);

        Reseller reseller = resellerRepository.findByUuid(resellerUuid)
                .orElseThrow(() -> new IllegalArgumentException("Reseller not found: " + resellerUuid));

        return mapToDetailResponse(reseller);
    }

    @Override
    public WithdrawalListResponse getWithdrawals(Pageable pageable) {
        log.info("Admin fetching all PENDING withdrawals - page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSize());

        Page<ResellerWithdrawalRequest> page = withdrawalRepository
                .findByStatus(WithdrawalStatus.PENDING, pageable);

        List<WithdrawalListResponse.WithdrawalSummary> summaries = page.getContent()
                .stream()
                .map(this::mapToWithdrawalSummary)
                .toList();

        return WithdrawalListResponse.builder()
                .withdrawals(summaries)
                .currentPage(page.getNumber())
                .totalPages(page.getTotalPages())
                .totalWithdrawals(page.getTotalElements())
                .pageSize(page.getSize())
                .build();
    }

    @Override
    public WithdrawalListResponse getAllWithdrawals(Pageable pageable) {
        log.info("Admin fetching ALL withdrawals - page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSize());

        Page<ResellerWithdrawalRequest> page = withdrawalRepository.findAll(pageable);

        List<WithdrawalListResponse.WithdrawalSummary> summaries = page.getContent()
                .stream()
                .map(this::mapToWithdrawalSummary)
                .toList();

        return WithdrawalListResponse.builder()
                .withdrawals(summaries)
                .currentPage(page.getNumber())
                .totalPages(page.getTotalPages())
                .totalWithdrawals(page.getTotalElements())
                .pageSize(page.getSize())
                .build();
    }

    @Override
    @Transactional("resellerTransactionManager")
    public void updateWithdrawalStatus(String withdrawalUuid, String newStatus, Long adminId, String notes) {
        log.info("Admin {} changing withdrawal {} status to {}", adminId, withdrawalUuid, newStatus);

        // Only APPROVED and REJECTED are accepted via this endpoint
        String upperStatus = newStatus.toUpperCase();
        if (!upperStatus.equals("APPROVED") && !upperStatus.equals("REJECTED")) {
            throw new IllegalArgumentException(
                    "Invalid status: '" + newStatus + "'. Accepted values: APPROVED, REJECTED");
        }

        ResellerWithdrawalRequest withdrawal = withdrawalRepository.findByUuid(withdrawalUuid)
                .orElseThrow(() -> new IllegalArgumentException("Withdrawal request not found: " + withdrawalUuid));


        WithdrawalStatus target = WithdrawalStatus.valueOf(upperStatus);

        withdrawal.setStatus(target);
        withdrawal.setProcessedByAdminId(adminId);
        withdrawal.setProcessedDate(LocalDateTime.now());
        withdrawal.setAdminNotes(notes);

        if (target == WithdrawalStatus.APPROVED) {
            withdrawal.setTransactionReference(notes);
            log.info("Withdrawal {} approved with transaction reference: {}", withdrawalUuid, notes);

            // Mark the related debit transaction as SUCCESS (if exists)
            var debitTx = transactionRepository.findFirstByReferenceWithdrawalIdAndTypeOrderByDateCreatedDesc(withdrawal.getId(), org.psint.beyosclothing.modules.resellers.entity.TransactionType.WITHDRAWAL_DEBIT);
            if (debitTx != null) {
                debitTx.setStatus(WalletTransactionStatus.SUCCESS);
                transactionRepository.save(debitTx);
            }
        }

        if (target == WithdrawalStatus.REJECTED) {
            withdrawal.setRejectionReason(notes);
            // Refund the reseller's wallet
            walletService.reverseWithdrawal(withdrawal.getResellerId(), withdrawal.getAmount(), withdrawal.getId());
            log.info("Wallet refunded for rejected withdrawal: {}", withdrawalUuid);
        }

        withdrawalRepository.save(withdrawal);
        log.info("Withdrawal {} status updated: PENDING → {}", withdrawalUuid, target);
    }

    private WithdrawalListResponse.WithdrawalSummary mapToWithdrawalSummary(ResellerWithdrawalRequest w) {
        ResellerBankAccount bankAccount = bankAccountRepository.findById(w.getBankAccountId()).orElse(null);

        return WithdrawalListResponse.WithdrawalSummary.builder()
                .uuid(w.getUuid())
                .amount(w.getAmount())
                .status(w.getStatus().name())
                .requestedDate(w.getRequestedDate())
                .bankName(bankAccount != null ? bankAccount.getBankName() : "Unknown")
                .accountNumber(bankAccount != null ? bankAccount.getAccountNumber() : null)
                .accountHolderName(bankAccount != null ? bankAccount.getAccountHolderName() : null)
                .build();
    }

    private AdminResellerListResponse.ResellerSummary mapToSummary(Reseller reseller) {
        return AdminResellerListResponse.ResellerSummary.builder()
                .uuid(reseller.getUuid())
                .fullName(reseller.getFullName())
                .email(reseller.getEmail())
                .phone(reseller.getPhone())
                .status(reseller.getStatus().name())
                .registrationDate(reseller.getDateCreated())
                .currentBalance(reseller.getCreditBalance())
                .orderCount(0L) // TODO: Implement order count
                .totalSales(BigDecimal.ZERO) // TODO: Implement total sales
                .totalProfit(BigDecimal.ZERO) // TODO: Implement total profit
                .isActive(reseller.getIsActive())
                .build();
    }

    private AdminResellerDetailResponse mapToDetailResponse(Reseller reseller) {
        // Build profile section
        AdminResellerDetailResponse.ResellerProfile profile = AdminResellerDetailResponse.ResellerProfile.builder()
                .uuid(reseller.getUuid())
                .fullName(reseller.getFullName())
                .email(reseller.getEmail())
                .phone(reseller.getPhone())
                .address(String.format("%s, %s, %s, %s %s",
                    reseller.getAddressLine1(),
                    reseller.getCity(),
                    reseller.getDistrict(),
                    reseller.getProvince(),
                    reseller.getPostalCode()))
                .status(reseller.getStatus().name())
                .registrationDate(reseller.getDateCreated())
                .approvalDate(null) // TODO: Track approval date
                .isActive(reseller.getIsActive())
                .allowPriceOverride(reseller.getAllowPriceOverride())
                .minMarkupPct(reseller.getMinAllowedMarkupPct())
                .maxMarkupPct(reseller.getMaxAllowedMarkupPct())
                .build();

        // Build performance metrics section
        AdminResellerDetailResponse.PerformanceMetrics metrics = AdminResellerDetailResponse.PerformanceMetrics.builder()
                .totalOrders(0L) // TODO: Implement
                .totalSales(BigDecimal.ZERO) // TODO: Implement
                .totalProfit(BigDecimal.ZERO) // TODO: Implement
                .averageOrderValue(BigDecimal.ZERO) // TODO: Implement
                .ordersThisMonth(0L) // TODO: Implement
                .salesThisMonth(BigDecimal.ZERO) // TODO: Implement
                .build();

        // Build wallet info section
        AdminResellerDetailResponse.WalletInfo wallet = AdminResellerDetailResponse.WalletInfo.builder()
                .currentBalance(reseller.getCreditBalance())
                .creditLimit(reseller.getCreditLimit())
                .availableCredit(reseller.getAvailableCredit())
                .build();

        return AdminResellerDetailResponse.builder()
                .profile(profile)
                .metrics(metrics)
                .recentOrders(java.util.Collections.emptyList()) // TODO: Implement
                .wallet(wallet)
                .bankAccounts(java.util.Collections.emptyList()) // TODO: Implement
                .withdrawalHistory(java.util.Collections.emptyList()) // TODO: Implement
                .build();
    }
}
