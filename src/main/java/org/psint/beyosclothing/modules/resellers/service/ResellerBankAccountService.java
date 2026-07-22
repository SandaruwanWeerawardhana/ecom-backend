package org.psint.beyosclothing.modules.resellers.service;

import org.psint.beyosclothing.modules.resellers.dto.request.AddBankAccountRequest;
import org.psint.beyosclothing.modules.resellers.dto.request.UpdateBankAccountRequest;
import org.psint.beyosclothing.modules.resellers.dto.response.BankAccountResponse;

import java.util.List;

/**
 * Service interface for Reseller Bank Account operations
 */
public interface ResellerBankAccountService {

    /**
     * Add bank account for reseller
     * @param resellerId Reseller ID
     * @param request Add bank account request
     * @return Bank account response
     */
    BankAccountResponse addBankAccount(Long resellerId, AddBankAccountRequest request);

    /**
     * Update bank account
     * @param resellerId Reseller ID
     * @param bankAccountUuid Bank account UUID
     * @param request Update request
     * @return Updated bank account
     */
    BankAccountResponse updateBankAccount(Long resellerId, String bankAccountUuid, UpdateBankAccountRequest request);

    /**
     * Delete (soft delete) bank account
     * @param resellerId Reseller ID
     * @param bankAccountUuid Bank account UUID
     */
    void deleteBankAccount(Long resellerId, String bankAccountUuid);

    /**
     * Get all bank accounts for reseller
     * @param resellerId Reseller ID
     * @return List of bank accounts
     */
    List<BankAccountResponse> getBankAccounts(Long resellerId);

    /**
     * Get bank account by UUID
     * @param resellerId Reseller ID
     * @param bankAccountUuid Bank account UUID
     * @return Bank account response
     */
    BankAccountResponse getBankAccountByUuid(Long resellerId, String bankAccountUuid);

    /**
     * Set primary account
     * @param resellerId Reseller ID
     * @param bankAccountUuid Bank account UUID
     */
    void setPrimaryAccount(Long resellerId, String bankAccountUuid);
}

