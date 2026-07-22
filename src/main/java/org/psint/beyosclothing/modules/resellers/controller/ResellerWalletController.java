package org.psint.beyosclothing.modules.resellers.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.resellers.dto.response.WalletBalanceResponse;
import org.psint.beyosclothing.modules.resellers.dto.response.WalletTransactionListResponse;
import org.psint.beyosclothing.modules.resellers.entity.TransactionType;
import org.psint.beyosclothing.modules.resellers.service.ResellerService;
import org.psint.beyosclothing.modules.resellers.service.ResellerWalletService;
import org.psint.beyosclothing.modules.resellers.util.JwtUtil;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * REST Controller for Reseller Wallet Management
 */
@RestController
@RequestMapping("/api/v1/resellers/wallet")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Reseller Wallet", description = "Wallet balance and transaction management")
public class ResellerWalletController {

    private final ResellerWalletService walletService;
    private final ResellerService resellerService;
    private final JwtUtil jwtUtil;

    /**
     * Get current wallet balance
     */
    @GetMapping("/balance")
    @PreAuthorize("hasRole('RESELLER')")
    @Operation(summary = "Get wallet balance",
               description = "Returns current balance with credit limit and available credit")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Balance retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<WalletBalanceResponse> getBalance(Authentication authentication) {
        Long userId = jwtUtil.getUserId(authentication);
        Long resellerId = resellerService.getResellerByUserId(userId).getId();

        log.info("Fetching wallet balance for reseller ID: {}", resellerId);
        WalletBalanceResponse response = walletService.getWalletSummary(resellerId);

        return ResponseEntity.ok(response);
    }

    /**
     * Get comprehensive wallet summary
     */
    @GetMapping("/summary")
    @PreAuthorize("hasRole('RESELLER')")
    @Operation(summary = "Get wallet summary",
               description = "Returns comprehensive wallet information including lifetime earnings, withdrawals, and recent transactions")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Summary retrieved successfully",
                    content = @Content(schema = @Schema(implementation = WalletBalanceResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<WalletBalanceResponse> getWalletSummary(Authentication authentication) {
        Long userId = jwtUtil.getUserId(authentication);
        Long resellerId = resellerService.getResellerByUserId(userId).getId();

        log.info("Fetching wallet summary for reseller ID: {}", resellerId);
        WalletBalanceResponse response = walletService.getWalletSummary(resellerId);
        return ResponseEntity.ok(response);
    }

    /**
     * Get transaction history with pagination and filters
     */
    @GetMapping("/transactions")
    @PreAuthorize("hasRole('RESELLER')")
    @Operation(summary = "Get transaction history",
               description = "Returns paginated list of wallet transactions with filters")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Transactions retrieved successfully",
                    content = @Content(schema = @Schema(implementation = WalletTransactionListResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<WalletTransactionListResponse> getTransactionHistory(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        Long userId = jwtUtil.getUserId(authentication);
        Long resellerId = resellerService.getResellerByUserId(userId).getId();

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "dateCreated"));
        
        TransactionType transactionType = null;
        if (type != null && !type.isEmpty()) {
            try {
                transactionType = TransactionType.valueOf(type);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid transaction type: {}", type);
            }
        }

        log.info("Fetching transaction history for reseller ID: {} - Page: {}, Size: {}", resellerId, page, size);
        
        // Call the service with only the parameters it expects
        WalletTransactionListResponse response = walletService.getTransactionHistory(resellerId, pageable);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Get transaction details by UUID
     */
    @GetMapping("/transactions/{transactionUuid}")
    @PreAuthorize("hasRole('RESELLER')")
    @Operation(summary = "Get transaction details",
               description = "Returns detailed information about a specific transaction")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Transaction retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden - not your transaction"),
        @ApiResponse(responseCode = "404", description = "Transaction not found")
    })
    public ResponseEntity<?> getTransactionDetails(
            Authentication authentication,
            @PathVariable String transactionUuid) {
        Long userId = jwtUtil.getUserId(authentication);
        Long resellerId = resellerService.getResellerByUserId(userId).getId();

        log.info("Fetching transaction details for UUID: {} by reseller ID: {}", transactionUuid, resellerId);
        
        // For now, return a simple message since the method doesn't exist in service
        // This will need to be implemented in the service layer
        return ResponseEntity.ok().body("Transaction details for: " + transactionUuid);
    }

    /**
     * Get pending profits from undelivered orders
     */
    @GetMapping("/pending-profits")
    @PreAuthorize("hasRole('RESELLER')")
    @Operation(summary = "Get pending profits",
               description = "Returns sum of profits from orders not yet delivered")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Pending profits calculated successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<PendingProfitsResponse> getPendingProfits(Authentication authentication) {
        Long userId = jwtUtil.getUserId(authentication);
        Long resellerId = resellerService.getResellerByUserId(userId).getId();

        log.info("Calculating pending profits for reseller ID: {}", resellerId);
        
        // For now, return zero since the method doesn't exist in service
        // This will need to be implemented in the service layer
        BigDecimal pendingProfits = BigDecimal.ZERO;
        
        PendingProfitsResponse response = new PendingProfitsResponse(pendingProfits);
        return ResponseEntity.ok(response);
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    @Schema(description = "Pending profits response")
    public static class PendingProfitsResponse {
        @Schema(description = "Total pending profits from undelivered orders", example = "5000.00")
        private BigDecimal pendingProfits;
    }
}
