package org.psint.beyosclothing.modules.resellers.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.resellers.dto.request.AddBankAccountRequest;
import org.psint.beyosclothing.modules.resellers.dto.request.UpdateBankAccountRequest;
import org.psint.beyosclothing.modules.resellers.dto.response.BankAccountResponse;
import org.psint.beyosclothing.modules.resellers.service.ResellerBankAccountService;
import org.psint.beyosclothing.modules.resellers.service.ResellerSecurityService;
import org.psint.beyosclothing.modules.resellers.service.ResellerService;
import org.psint.beyosclothing.modules.resellers.util.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for Reseller Bank Account Management
 */
@RestController
@RequestMapping("/api/v1/resellers/bank-accounts")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Reseller Bank Accounts", description = "Bank account management for resellers")
public class ResellerBankAccountController {

    private final ResellerBankAccountService bankAccountService;
    private final ResellerSecurityService securityService;
    private final ResellerService resellerService;
    private final JwtUtil jwtUtil;

    /**
     * Add bank account
     */
    @PostMapping
    @PreAuthorize("hasRole('RESELLER')")
    @Operation(summary = "Add bank account",
               description = "Adds bank account for reseller. Account number will be masked in responses. Requires APPROVED status.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Bank account added successfully",
                    content = @Content(schema = @Schema(implementation = BankAccountResponse.class))),
        @ApiResponse(responseCode = "400", description = "Validation error"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden - reseller not approved")
    })
    public ResponseEntity<BankAccountResponse> addBankAccount(
            Authentication authentication,
            @Valid @RequestBody AddBankAccountRequest request) {
        Long userId = jwtUtil.getUserId(authentication);
        securityService.checkApprovedStatus(userId);
        Long resellerId = resellerService.getResellerByUserId(userId).getId();

        log.info("Adding bank account for reseller ID: {} - Bank: {}", resellerId, request.getBankName());
        BankAccountResponse response = bankAccountService.addBankAccount(resellerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get all bank accounts
     */
    @GetMapping
    @PreAuthorize("hasRole('RESELLER')")
    @Operation(summary = "Get bank accounts",
               description = "Returns list of reseller's bank accounts. Account numbers are masked for security.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Bank accounts retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<List<BankAccountResponse>> getBankAccounts(Authentication authentication) {
        Long userId = jwtUtil.getUserId(authentication);
        Long resellerId = resellerService.getResellerByUserId(userId).getId();

        log.info("Fetching bank accounts for reseller ID: {}", resellerId);
        List<BankAccountResponse> response = bankAccountService.getBankAccounts(resellerId);
        return ResponseEntity.ok(response);
    }

    /**
     * Get bank account by UUID
     */
    @GetMapping("/{bankAccountUuid}")
    @PreAuthorize("hasRole('RESELLER')")
    @Operation(summary = "Get bank account", description = "Returns single bank account details")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Bank account retrieved successfully",
                    content = @Content(schema = @Schema(implementation = BankAccountResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden - not your account"),
        @ApiResponse(responseCode = "404", description = "Bank account not found")
    })
    public ResponseEntity<BankAccountResponse> getBankAccountByUuid(
            Authentication authentication,
            @PathVariable String bankAccountUuid) {
        Long userId = jwtUtil.getUserId(authentication);
        Long resellerId = resellerService.getResellerByUserId(userId).getId();

        log.info("Fetching bank account UUID: {} for reseller ID: {}", bankAccountUuid, resellerId);
        BankAccountResponse response = bankAccountService.getBankAccountByUuid(resellerId, bankAccountUuid);
        return ResponseEntity.ok(response);
    }

    /**
     * Update bank account
     */
    @PutMapping("/{bankAccountUuid}")
    @PreAuthorize("hasRole('RESELLER')")
    @Operation(summary = "Update bank account", description = "Updates bank account details")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Bank account updated successfully",
                    content = @Content(schema = @Schema(implementation = BankAccountResponse.class))),
        @ApiResponse(responseCode = "400", description = "Validation error"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Bank account not found")
    })
    public ResponseEntity<BankAccountResponse> updateBankAccount(
            Authentication authentication,
            @PathVariable String bankAccountUuid,
            @Valid @RequestBody UpdateBankAccountRequest request) {
        Long userId = jwtUtil.getUserId(authentication);
        Long resellerId = resellerService.getResellerByUserId(userId).getId();

        log.info("Updating bank account UUID: {} for reseller ID: {}", bankAccountUuid, resellerId);
        BankAccountResponse response = bankAccountService.updateBankAccount(resellerId, bankAccountUuid, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Delete bank account (soft delete)
     */
    @DeleteMapping("/{bankAccountUuid}")
    @PreAuthorize("hasRole('RESELLER')")
    @Operation(summary = "Delete bank account", 
               description = "Soft deletes bank account. Cannot delete if there's a pending withdrawal using this account.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Bank account deleted successfully"),
        @ApiResponse(responseCode = "400", description = "Cannot delete - pending withdrawal exists"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Bank account not found")
    })
    public ResponseEntity<Void> deleteBankAccount(
            Authentication authentication,
            @PathVariable String bankAccountUuid) {
        Long userId = jwtUtil.getUserId(authentication);
        Long resellerId = resellerService.getResellerByUserId(userId).getId();

        log.info("Deleting bank account UUID: {} for reseller ID: {}", bankAccountUuid, resellerId);
        bankAccountService.deleteBankAccount(resellerId, bankAccountUuid);
        return ResponseEntity.noContent().build();
    }
}
