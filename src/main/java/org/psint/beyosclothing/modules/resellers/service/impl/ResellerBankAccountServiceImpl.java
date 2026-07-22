package org.psint.beyosclothing.modules.resellers.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.resellers.dto.request.AddBankAccountRequest;
import org.psint.beyosclothing.modules.resellers.dto.request.UpdateBankAccountRequest;
import org.psint.beyosclothing.modules.resellers.dto.response.BankAccountResponse;
import org.psint.beyosclothing.modules.resellers.entity.ResellerBankAccount;
import org.psint.beyosclothing.modules.resellers.repository.ResellerBankAccountRepository;
import org.psint.beyosclothing.modules.resellers.service.ResellerBankAccountService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of Reseller Bank Account Service
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional("resellerTransactionManager")
public class ResellerBankAccountServiceImpl implements ResellerBankAccountService {

    private final ResellerBankAccountRepository bankAccountRepository;

    @Override
    public BankAccountResponse addBankAccount(Long resellerId, AddBankAccountRequest request) {
        // If setting as primary, unset other primary accounts
        if (Boolean.TRUE.equals(request.getIsPrimary())) {
            unsetPrimaryAccounts(resellerId);
        }

        ResellerBankAccount bankAccount = ResellerBankAccount.builder()
                .resellerId(resellerId)
                .bankName(request.getBankName())
                .accountHolderName(request.getAccountHolderName())
                .accountNumber(request.getAccountNumber())
                .branchName(request.getBranchName())
                .branchCode(request.getBranchCode())
                .swiftCode(request.getSwiftCode())
                .isPrimary(request.getIsPrimary())
                .isActive(true)
                .isVerified(false)
                .build();

        bankAccount = bankAccountRepository.save(bankAccount);

        // Notify admin for verification
        notifyAdminForVerification(bankAccount);

        log.info("Bank account added for reseller: {}", resellerId);

        return mapToBankAccountResponse(bankAccount, false);
    }

    @Override
    public BankAccountResponse updateBankAccount(Long resellerId, String bankAccountUuid, UpdateBankAccountRequest request) {
        ResellerBankAccount bankAccount = bankAccountRepository.findByResellerIdAndUuid(resellerId, bankAccountUuid)
                .orElseThrow(() -> new IllegalArgumentException("Bank account not found"));

        // Check if used in pending withdrawals
        // TODO: Add validation

        // If setting as primary, unset other primary accounts
        if (Boolean.TRUE.equals(request.getIsPrimary())) {
            unsetPrimaryAccounts(resellerId);
        }

        bankAccount.setBankName(request.getBankName());
        bankAccount.setAccountHolderName(request.getAccountHolderName());
        bankAccount.setBranchName(request.getBranchName());
        bankAccount.setBranchCode(request.getBranchCode());
        bankAccount.setSwiftCode(request.getSwiftCode());
        bankAccount.setIsPrimary(request.getIsPrimary());

        bankAccount = bankAccountRepository.save(bankAccount);

        log.info("Bank account updated: {}", bankAccountUuid);

        return mapToBankAccountResponse(bankAccount, false);
    }

    @Override
    public void deleteBankAccount(Long resellerId, String bankAccountUuid) {
        ResellerBankAccount bankAccount = bankAccountRepository.findByResellerIdAndUuid(resellerId, bankAccountUuid)
                .orElseThrow(() -> new IllegalArgumentException("Bank account not found"));

        // Check if used in pending withdrawals
        // TODO: Add validation

        // Soft delete
        bankAccount.setIsActive(false);
        bankAccountRepository.save(bankAccount);

        // If was primary, set another account as primary
        if (bankAccount.isPrimary()) {
            setAnotherAccountAsPrimary(resellerId);
        }

        log.info("Bank account deleted (soft): {}", bankAccountUuid);
    }

    @Override
    public List<BankAccountResponse> getBankAccounts(Long resellerId) {
        List<ResellerBankAccount> accounts = bankAccountRepository.findByResellerIdAndIsActive(resellerId, true);
        return accounts.stream()
                .map(account -> mapToBankAccountResponse(account, false))
                .collect(Collectors.toList());
    }

    @Override
    public BankAccountResponse getBankAccountByUuid(Long resellerId, String bankAccountUuid) {
        ResellerBankAccount bankAccount = bankAccountRepository.findByResellerIdAndUuid(resellerId, bankAccountUuid)
                .orElseThrow(() -> new IllegalArgumentException("Bank account not found"));
        return mapToBankAccountResponse(bankAccount, false);
    }

    @Override
    public void setPrimaryAccount(Long resellerId, String bankAccountUuid) {
        ResellerBankAccount bankAccount = bankAccountRepository.findByResellerIdAndUuid(resellerId, bankAccountUuid)
                .orElseThrow(() -> new IllegalArgumentException("Bank account not found"));

        unsetPrimaryAccounts(resellerId);

        bankAccount.setIsPrimary(true);
        bankAccountRepository.save(bankAccount);

        log.info("Primary account set: {}", bankAccountUuid);
    }

    // Helper methods
    private void unsetPrimaryAccounts(Long resellerId) {
        List<ResellerBankAccount> accounts = bankAccountRepository.findByResellerIdAndIsActive(resellerId, true);
        accounts.forEach(account -> {
            if (account.isPrimary()) {
                account.setIsPrimary(false);
                bankAccountRepository.save(account);
            }
        });
    }

    private void setAnotherAccountAsPrimary(Long resellerId) {
        List<ResellerBankAccount> accounts = bankAccountRepository.findByResellerIdAndIsActive(resellerId, true);
        if (!accounts.isEmpty()) {
            ResellerBankAccount firstAccount = accounts.get(0);
            firstAccount.setIsPrimary(true);
            bankAccountRepository.save(firstAccount);
        }
    }

    private void notifyAdminForVerification(ResellerBankAccount bankAccount) {
        log.info("Sending admin notification for bank account verification: {}", bankAccount.getUuid());
        // TODO: Send notification
    }

    private BankAccountResponse mapToBankAccountResponse(ResellerBankAccount account, boolean showFullAccountNumber) {
        return BankAccountResponse.builder()
                .uuid(account.getUuid())
                .bankName(account.getBankName())
                .accountHolderName(account.getAccountHolderName())
                .maskedAccountNumber(account.getMaskedAccountNumber())
                .accountNumber(showFullAccountNumber ? account.getAccountNumber() : null)
                .branchName(account.getBranchName())
                .branchCode(account.getBranchCode())
                .swiftCode(account.getSwiftCode())
                .isPrimary(account.getIsPrimary())
                .isActive(account.getIsActive())
                .isVerified(account.getIsVerified())
                .dateCreated(account.getDateCreated())
                .build();
    }
}

