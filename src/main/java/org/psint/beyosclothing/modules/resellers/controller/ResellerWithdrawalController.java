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
import org.psint.beyosclothing.modules.resellers.dto.request.WithdrawalRequestRequest;
import org.psint.beyosclothing.modules.resellers.dto.response.WithdrawalListResponse;
import org.psint.beyosclothing.modules.resellers.dto.response.WithdrawalRequestResponse;
import org.psint.beyosclothing.modules.resellers.service.ResellerSecurityService;
import org.psint.beyosclothing.modules.resellers.service.ResellerService;
import org.psint.beyosclothing.modules.resellers.service.ResellerWithdrawalService;
import org.psint.beyosclothing.modules.resellers.util.JwtUtil;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * REST Controller for Reseller Withdrawal Management
 */
@RestController
@RequestMapping("/api/v1/resellers/withdrawals")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Reseller Withdrawals", description = "Withdrawal request management for resellers")
public class ResellerWithdrawalController {

    private final ResellerWithdrawalService withdrawalService;
    private final ResellerSecurityService securityService;
    private final ResellerService resellerService;
    private final JwtUtil jwtUtil;

    /**
     * Create withdrawal request
     */
    @PostMapping
    @PreAuthorize("hasRole('RESELLER')")
    @Operation(summary = "Request withdrawal",
               description = "Creates withdrawal request. Balance is deducted immediately. Requires APPROVED status. Minimum: 1000 LKR.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Withdrawal request created successfully",
                    content = @Content(schema = @Schema(implementation = WithdrawalRequestResponse.class))),
        @ApiResponse(responseCode = "400", description = "Validation error - insufficient balance, pending request exists, or below minimum"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden - reseller not approved"),
        @ApiResponse(responseCode = "404", description = "Bank account not found")
    })
    public ResponseEntity<WithdrawalRequestResponse> createWithdrawalRequest(
            Authentication authentication,
            @Valid @RequestBody WithdrawalRequestRequest request) {
        Long userId = jwtUtil.getUserId(authentication);
        securityService.checkApprovedStatus(userId);
        Long resellerId = resellerService.getResellerByUserId(userId).getId();

        log.info("Creating withdrawal request for reseller ID: {} - Amount: {}", resellerId, request.getAmount());
        WithdrawalRequestResponse response = withdrawalService.createWithdrawalRequest(resellerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get withdrawal requests with pagination and optional date range filter
     */
    @GetMapping
    @PreAuthorize("hasRole('RESELLER')")
    @Operation(summary = "Get withdrawal requests",
               description = "Returns paginated list of withdrawal requests. Optional startDate and endDate (ISO date or datetime) can be provided to filter by requested date")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Withdrawal requests retrieved successfully",
                    content = @Content(schema = @Schema(implementation = WithdrawalListResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<WithdrawalListResponse> getWithdrawalRequests(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        Long userId = jwtUtil.getUserId(authentication);
        Long resellerId = resellerService.getResellerByUserId(userId).getId();

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "dateCreated"));
        log.info("Fetching withdrawal requests for reseller ID: {} - Page: {}, Size: {} - startDate: {}, endDate: {}",
                resellerId, page, size, startDate, endDate);


        LocalDateTime start = (startDate != null && !startDate.isBlank()) ? LocalDate.parse(startDate).atStartOfDay() : null;
        LocalDateTime end = (endDate != null && !endDate.isBlank()) ? LocalDateTime.of(LocalDate.parse(endDate), LocalTime.MAX) : null;

        // Call the service with optional status and date range (may be null)
        WithdrawalListResponse response = withdrawalService.getWithdrawalRequests(resellerId, pageable, status, start, end);
        return ResponseEntity.ok(response);
    }

    /**
     * Get pending withdrawals
     */
    @GetMapping("/pending")
    @PreAuthorize("hasRole('RESELLER')")
    @Operation(summary = "Get pending withdrawal requests",
            description = "Returns only pending withdrawal requests")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pending withdrawals retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<WithdrawalListResponse> getPendingWithdrawals(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        Long userId = jwtUtil.getUserId(authentication);
        Long resellerId = resellerService.getResellerByUserId(userId).getId();

        log.info("Fetching pending withdrawals for reseller ID: {} - Page: {}, Size: {} - startDate: {}, endDate: {}", resellerId, page, size, startDate, endDate);

        LocalDateTime start = (startDate != null && !startDate.isBlank()) ? LocalDate.parse(startDate).atStartOfDay() : null;
        LocalDateTime end = (endDate != null && !endDate.isBlank()) ? LocalDateTime.of(LocalDate.parse(endDate), LocalTime.MAX) : null;

        // Use the general getWithdrawalRequests with status = PENDING so filtering logic is consistent
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "dateCreated"));
        WithdrawalListResponse response = withdrawalService.getWithdrawalRequests(resellerId, pageable, "PENDING", start, end);
        return ResponseEntity.ok(response);
    }

    /**
     * Get withdrawal by UUID
     */
    @GetMapping("/{withdrawalUuid}")
    @PreAuthorize("hasRole('RESELLER')")
    @Operation(summary = "Get withdrawal details",
               description = "Returns detailed withdrawal request information")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Withdrawal retrieved successfully",
                    content = @Content(schema = @Schema(implementation = WithdrawalRequestResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden - not your withdrawal"),
        @ApiResponse(responseCode = "404", description = "Withdrawal not found")
    })
    public ResponseEntity<WithdrawalRequestResponse> getWithdrawalByUuid(
            Authentication authentication,
            @PathVariable String withdrawalUuid) {
        Long userId = jwtUtil.getUserId(authentication);
        Long resellerId = resellerService.getResellerByUserId(userId).getId();

        log.info("Fetching withdrawal details for UUID: {} by reseller ID: {}", withdrawalUuid, resellerId);
        WithdrawalRequestResponse response = withdrawalService.getWithdrawalByUuid(resellerId, withdrawalUuid);
        return ResponseEntity.ok(response);
    }

    /**
     * Cancel withdrawal request
     */
    @DeleteMapping("/{withdrawalUuid}")
    @PreAuthorize("hasRole('RESELLER')")
    @Operation(summary = "Cancel withdrawal",
               description = "Cancels withdrawal request and refunds amount to wallet. Only PENDING requests can be cancelled.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Withdrawal cancelled and refunded successfully"),
        @ApiResponse(responseCode = "400", description = "Cannot cancel - not in PENDING status"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden - not your withdrawal"),
        @ApiResponse(responseCode = "404", description = "Withdrawal not found")
    })
    public ResponseEntity<Void> cancelWithdrawalRequest(
            Authentication authentication,
            @PathVariable String withdrawalUuid) {
        Long userId = jwtUtil.getUserId(authentication);
        Long resellerId = resellerService.getResellerByUserId(userId).getId();

        log.info("Cancelling withdrawal: {} for reseller ID: {}", withdrawalUuid, resellerId);
        withdrawalService.cancelWithdrawalRequest(resellerId, withdrawalUuid);
        return ResponseEntity.noContent().build();
    }
}

