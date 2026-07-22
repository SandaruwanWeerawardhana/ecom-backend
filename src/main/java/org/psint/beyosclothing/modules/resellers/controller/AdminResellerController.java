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
import org.psint.beyosclothing.modules.resellers.dto.request.AdminResellerApprovalRequest;
import org.psint.beyosclothing.modules.resellers.dto.request.AdminUpdateResellerSettingsRequest;
import org.psint.beyosclothing.modules.resellers.dto.request.AdminWithdrawalActionRequest;
import org.psint.beyosclothing.modules.resellers.dto.response.AdminResellerDetailResponse;
import org.psint.beyosclothing.modules.resellers.dto.response.AdminResellerListResponse;
import org.psint.beyosclothing.modules.resellers.dto.response.ResellerProfileResponse;
import org.psint.beyosclothing.modules.resellers.dto.response.WithdrawalListResponse;
import org.psint.beyosclothing.modules.resellers.service.AdminResellerService;
import org.psint.beyosclothing.modules.resellers.service.ResellerService;
import org.psint.beyosclothing.modules.resellers.service.ResellerWithdrawalService;
import org.psint.beyosclothing.modules.resellers.util.JwtUtil;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for Admin Reseller Management
 */
@RestController
@RequestMapping("/api/v1/admin/resellers")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin - Reseller Management", description = "Admin endpoints for reseller approval and management")
public class AdminResellerController {

    private final ResellerService resellerService;
    private final ResellerWithdrawalService withdrawalService;
    private final AdminResellerService adminResellerService;
    private final JwtUtil jwtUtil;

    /**
     * Get all resellers with pagination (Admin only)
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all resellers",
               description = "Returns paginated list of resellers with key metrics")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Resellers retrieved successfully",
                    content = @Content(schema = @Schema(implementation = AdminResellerListResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden - admin role required")
    })
    public ResponseEntity<AdminResellerListResponse> getAllResellers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        log.info("Admin fetching resellers - Page: {}, Size: {}, Status: {}", page, size, status);

        Pageable pageable = PageRequest.of(page, size);
        AdminResellerListResponse response = adminResellerService.getAllResellers(pageable, status);
        return ResponseEntity.ok(response);
    }

    /**
     * Get reseller details (Admin only)
     */
    @GetMapping("/{resellerUuid}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get reseller details",
               description = "Returns complete reseller information with performance metrics")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Reseller details retrieved successfully",
                    content = @Content(schema = @Schema(implementation = AdminResellerDetailResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Reseller not found")
    })
    public ResponseEntity<AdminResellerDetailResponse> getResellerDetails(
            @PathVariable String resellerUuid) {
        log.info("Admin fetching reseller details: {}", resellerUuid);

        AdminResellerDetailResponse response = adminResellerService.getResellerDetails(resellerUuid);
        return ResponseEntity.ok(response);
    }

    /**
     * Approve or reject reseller (Admin only)
     */
    @PostMapping("/{resellerUuid}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Approve or reject reseller",
               description = "Admin approves or rejects reseller application with settings")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Reseller status updated successfully",
                    content = @Content(schema = @Schema(implementation = ResellerProfileResponse.class))),
        @ApiResponse(responseCode = "400", description = "Validation error or invalid status transition"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Reseller not found")
    })
    public ResponseEntity<ResellerProfileResponse> approveOrRejectReseller(
            Authentication authentication,
            @PathVariable String resellerUuid,
            @Valid @RequestBody AdminResellerApprovalRequest request) {
        Long adminId = extractAdminId(authentication);

        log.info("Admin {} processing reseller: {} - Status: {}", adminId, resellerUuid, request.getStatus());

        if ("APPROVED".equals(request.getStatus())) {
            resellerService.approveReseller(
                    resellerUuid,
                    request.getAllowPriceOverride(),
                    request.getMinAllowedMarkupPct(),
                    request.getMaxAllowedMarkupPct(),
                    request.getCreditLimit(),
                    adminId
            );
        } else if ("REJECTED".equals(request.getStatus())) {
            resellerService.rejectReseller(resellerUuid, request.getRejectionReason(), adminId);
        } else {
            throw new IllegalArgumentException("Invalid status. Use APPROVED or REJECTED");
        }

        // Return updated profile
        return ResponseEntity.ok(ResellerProfileResponse.builder()
                .uuid(resellerUuid)
                .status(request.getStatus())
                .build());
    }

    /**
     * Suspend reseller (Admin only)
     */
    @PostMapping("/{resellerUuid}/suspend")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Suspend reseller",
               description = "Admin suspends an approved reseller account")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Reseller suspended successfully"),
        @ApiResponse(responseCode = "400", description = "Cannot suspend - invalid status"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Reseller not found")
    })
    public ResponseEntity<Void> suspendReseller(
            Authentication authentication,
            @PathVariable String resellerUuid,
            @RequestParam(required = false) String suspensionReason) {
        Long adminId = extractAdminId(authentication);

        // default missing/blank reason to a safe value
        if (suspensionReason == null || suspensionReason.isBlank()) {
            suspensionReason = "Not provided";
        }

        log.info("Admin {} suspending reseller: {}", adminId, resellerUuid);
        resellerService.suspendReseller(resellerUuid, suspensionReason, adminId);
        return ResponseEntity.ok().build();
    }

    /**
     * Reactivate reseller (Admin only)
     */
    @PostMapping("/{resellerUuid}/reactivate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reactivate reseller",
               description = "Admin reactivates a suspended reseller account")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Reseller reactivated successfully"),
        @ApiResponse(responseCode = "400", description = "Cannot reactivate - not suspended"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Reseller not found")
    })
    public ResponseEntity<Void> reactivateReseller(
            Authentication authentication,
            @PathVariable String resellerUuid) {
        Long adminId = extractAdminId(authentication);

        log.info("Admin {} reactivating reseller: {}", adminId, resellerUuid);
        resellerService.reactivateReseller(resellerUuid, adminId);
        return ResponseEntity.ok().build();
    }

    /**
     * Update reseller settings (Admin only)
     */
    @PutMapping("/{resellerUuid}/settings")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update reseller settings",
               description = "Admin updates reseller pricing rules and credit limit")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Settings updated successfully"),
        @ApiResponse(responseCode = "400", description = "Validation error"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Reseller not found")
    })
    public ResponseEntity<Void> updateResellerSettings(
            Authentication authentication,
            @PathVariable String resellerUuid,
            @Valid @RequestBody AdminUpdateResellerSettingsRequest request) {
        Long adminId = extractAdminId(authentication);

        log.info("Admin {} updating settings for reseller: {}", adminId, resellerUuid);
        resellerService.updateSettings(
                resellerUuid,
                request.getAllowPriceOverride(),
                request.getMinAllowedMarkupPct(),
                request.getMaxAllowedMarkupPct(),
                request.getCreditLimit(),
                adminId
        );
        return ResponseEntity.ok().build();
    }

    /**
     * Process withdrawal request (Admin only)
     */
    @PostMapping("/withdrawals/{withdrawalUuid}/process")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Process withdrawal request",
               description = "Admin approves or rejects withdrawal request")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Withdrawal processed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid action or status"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Withdrawal not found")
    })
    public ResponseEntity<Void> processWithdrawal(
            Authentication authentication,
            @PathVariable String withdrawalUuid,
            @RequestParam String action,
            @Valid @RequestBody AdminWithdrawalActionRequest request) {
        Long adminId = extractAdminId(authentication);

        log.info("Admin {} processing withdrawal: {} - Action: {}", adminId, withdrawalUuid, action);

        if ("APPROVED".equalsIgnoreCase(action)) {
            withdrawalService.approveWithdrawal(
                    withdrawalUuid,
                    adminId,
                    request.getTransactionReference(),
                    request.getNotes()
            );
        } else if ("REJECTED".equalsIgnoreCase(action)) {
            withdrawalService.rejectWithdrawal(
                    withdrawalUuid,
                    adminId,
                    request.getNotes(),
                    request.getNotes()
            );
        } else {
            throw new IllegalArgumentException("Invalid action. Use APPROVED or REJECTED");
        }

        return ResponseEntity.ok().build();
    }

    /**
     * Mark withdrawal as completed (Admin only)
     */
    @PostMapping("/withdrawals/{withdrawalUuid}/complete")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Mark withdrawal as completed",
               description = "Admin confirms payment has been transferred")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Withdrawal marked as completed"),
        @ApiResponse(responseCode = "400", description = "Invalid status transition"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Withdrawal not found")
    })
    public ResponseEntity<Void> markWithdrawalCompleted(
            Authentication authentication,
            @PathVariable String withdrawalUuid) {
        Long adminId = extractAdminId(authentication);

        log.info("Admin {} marking withdrawal as completed: {}", adminId, withdrawalUuid);
        withdrawalService.markAsCompleted(withdrawalUuid, adminId);
        return ResponseEntity.ok().build();
    }

    /**
     * Mark withdrawal as failed (Admin only)
     */
    @PostMapping("/withdrawals/{withdrawalUuid}/fail")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Mark withdrawal as failed",
               description = "Admin marks withdrawal as failed and refunds wallet")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Withdrawal marked as failed and refunded"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Withdrawal not found")
    })
    public ResponseEntity<Void> markWithdrawalFailed(
            Authentication authentication,
            @PathVariable String withdrawalUuid,
            @RequestParam String failureReason) {
        Long adminId = extractAdminId(authentication);

        log.info("Admin {} marking withdrawal as failed: {}", adminId, withdrawalUuid);
        withdrawalService.markAsFailed(withdrawalUuid, adminId, failureReason);
        return ResponseEntity.ok().build();
    }

    /**
     * Admin: Get ALL withdrawal requests paginated (all statuses).
     * GET /api/v1/admin/resellers/withdrawals
     */
    @GetMapping("/withdrawals")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all withdrawal requests",
               description = "Returns paginated list of ALL withdrawal requests (all statuses) ordered by requested date descending")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "All withdrawals retrieved successfully",
                    content = @Content(schema = @Schema(implementation = WithdrawalListResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden - admin role required")
    })
    public ResponseEntity<WithdrawalListResponse> getAllWithdrawals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        log.info("Admin fetching ALL withdrawals - Page: {}, Size: {}", page, size);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "requestedDate"));
        WithdrawalListResponse response = adminResellerService.getAllWithdrawals(pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * Admin: Get all pending withdrawal requests paginated.
     */
    @GetMapping("/withdrawals/pending")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all pending withdrawal requests",
               description = "Returns paginated list of all PENDING withdrawal requests ordered by requested date descending")
    public ResponseEntity<WithdrawalListResponse> getWithdrawals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "requestedDate"));

        WithdrawalListResponse response = adminResellerService.getWithdrawals(pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * Admin: Change withdrawal status.
     * PATCH /api/v1/admin/resellers/withdrawals/{uuid}/status/{status}
     * Accepted values: APPROVED, REJECTED  (withdrawal must be PENDING)
     */
    @PatchMapping("/withdrawals/{uuid}/status/{status}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Approve or reject a withdrawal",
        description = "Changes a PENDING withdrawal status to APPROVED or REJECTED. " +
                      "Rejecting a withdrawal automatically refunds the reseller's wallet."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Status updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid status value or withdrawal is not PENDING"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden - admin role required"),
        @ApiResponse(responseCode = "404", description = "Withdrawal not found")
    })
    public ResponseEntity<Void> updateWithdrawalStatus(
            Authentication authentication,
            @PathVariable String uuid,
            @PathVariable String status,
            @RequestParam(required = false) String notes) {

        Long adminId = extractAdminId(authentication);
        log.info("Admin {} changing withdrawal {} status to {}", adminId, uuid, status);

        adminResellerService.updateWithdrawalStatus(uuid, status, adminId, notes);
        return ResponseEntity.ok().build();
    }

    private Long extractAdminId(Authentication authentication) {
        return jwtUtil.getUserId(authentication);
    }


}
