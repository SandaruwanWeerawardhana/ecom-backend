package org.psint.beyosclothing.modules.payment.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.common.dto.APIResponse;
import org.psint.beyosclothing.modules.payment.dto.request.CreatePaymentMethodRequest;
import org.psint.beyosclothing.modules.payment.dto.request.UpdatePaymentMethodRequest;
import org.psint.beyosclothing.modules.payment.dto.response.PaymentMethodResponse;
import org.psint.beyosclothing.modules.payment.entity.PaymentMethodEntity;
import org.psint.beyosclothing.modules.payment.service.PaymentMethodService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for Payment Method Management
 * Handles CRUD operations for payment methods
 */
@RestController
@RequestMapping("/api/v1/payment-methods")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payment Methods Controller", description = "Payment method management APIs")
public class PaymentMethodController {

    private final PaymentMethodService paymentMethodService;

    @PostMapping
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(
            summary = "Create a new payment method",
            description = "Creates a new payment method with the provided details. Admin access required.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Payment method created successfully",
                    content = @Content(schema = @Schema(implementation = PaymentMethodResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input data"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Authentication required"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin access required"),
            @ApiResponse(responseCode = "409", description = "Conflict - Payment method code already exists")
    })
    public ResponseEntity<APIResponse<PaymentMethodResponse>> createPaymentMethod(
            @Valid @RequestBody CreatePaymentMethodRequest request) {
        log.info("REST request to create payment method with code: {}", request.getCode());

        PaymentMethodResponse response = paymentMethodService.createPaymentMethod(request);

        log.info("Payment method created successfully with UUID: {}", response.getUuid());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(APIResponse.created(response));
    }

    /**
     * Update an existing payment method (Admin only)
     */
    @PutMapping("/{uuid}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(
            summary = "Update an existing payment method",
            description = "Updates the payment method details for the given UUID. Admin access required.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment method updated successfully",
                    content = @Content(schema = @Schema(implementation = PaymentMethodResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input data"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Authentication required"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin access required"),
            @ApiResponse(responseCode = "404", description = "Payment method not found"),
            @ApiResponse(responseCode = "409", description = "Conflict - Payment method code already exists")
    })
    public ResponseEntity<APIResponse<PaymentMethodResponse>> updatePaymentMethod(
            @Parameter(description = "UUID of the payment method to update", required = true)
            @PathVariable String uuid,
            @Valid @RequestBody UpdatePaymentMethodRequest request) {
        log.info("REST request to update payment method with UUID: {}", uuid);

        PaymentMethodResponse response = paymentMethodService.updatePaymentMethod(uuid, request);

        log.info("Payment method updated successfully with UUID: {}", uuid);
        return ResponseEntity.ok(APIResponse.success("Payment method updated successfully", response));
    }

    /**
     * Get payment method by UUID
     */
    @GetMapping("/{uuid}")
    @Operation(
            summary = "Get payment method by UUID",
            description = "Retrieves payment method details by UUID. Public access."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment method found",
                    content = @Content(schema = @Schema(implementation = PaymentMethodResponse.class))),
            @ApiResponse(responseCode = "404", description = "Payment method not found")
    })
    public ResponseEntity<APIResponse<PaymentMethodResponse>> getPaymentMethodByUuid(
            @Parameter(description = "UUID of the payment method", required = true)
            @PathVariable String uuid) {
        log.debug("REST request to get payment method by UUID: {}", uuid);

        PaymentMethodResponse response = paymentMethodService.getPaymentMethodByUuid(uuid);

        return ResponseEntity.ok(APIResponse.success("Payment method retrieved successfully", response));
    }

    /**
     * Get all payment methods with optional type filter
     */
    @GetMapping
    @Operation(
            summary = "Get all payment methods",
            description = "Retrieves all payment methods with optional filtering by type. Public access."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment methods retrieved successfully",
                    content = @Content(schema = @Schema(implementation = PaymentMethodResponse.class)))
    })
    public ResponseEntity<APIResponse<List<PaymentMethodResponse>>> getAllPaymentMethods(
            @Parameter(description = "Filter by payment type (ONLINE, OFFLINE, POS)")
            @RequestParam(required = false) PaymentMethodEntity.PaymentType type) {
        log.debug("REST request to get all payment methods with type filter: {}", type);

        List<PaymentMethodResponse> responses;
        if (type != null) {
            responses = paymentMethodService.getPaymentMethodsByType(type);
            log.debug("Found {} payment methods of type {}", responses.size(), type);
        } else {
            responses = paymentMethodService.getAllPaymentMethods();
            log.debug("Found {} payment methods", responses.size());
        }

        return ResponseEntity.ok(APIResponse.success("Payment methods retrieved successfully", responses));
    }


    @GetMapping("/active")
    @Operation(
            summary = "Get all active payment methods",
            description = "Retrieves only active payment methods, optionally filtered by type. Public access for customer-facing applications."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Active payment methods retrieved successfully",
                    content = @Content(schema = @Schema(implementation = PaymentMethodResponse.class)))
    })
    public ResponseEntity<APIResponse<List<PaymentMethodResponse>>> getActivePaymentMethods(
            @Parameter(description = "Filter by payment type (ONLINE, OFFLINE, POS)")
            @RequestParam(required = false) PaymentMethodEntity.PaymentType type) {
        log.debug("REST request to get active payment methods with type filter: {}", type);

        List<PaymentMethodResponse> responses;
        if (type != null) {
            responses = paymentMethodService.getActivePaymentMethodsByType(type);
            log.debug("Found {} active payment methods of type {}", responses.size(), type);
        } else {
            responses = paymentMethodService.getActivePaymentMethods();
            log.debug("Found {} active payment methods", responses.size());
        }

        return ResponseEntity.ok(APIResponse.success("Active payment methods retrieved successfully", responses));
    }

    @DeleteMapping("/{uuid}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(
            summary = "Delete a payment method",
            description = "Soft deletes a payment method by setting it to inactive. Admin access required.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment method deleted successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Authentication required"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin access required"),
            @ApiResponse(responseCode = "404", description = "Payment method not found")
    })
    public ResponseEntity<APIResponse<Void>> deletePaymentMethod(
            @Parameter(description = "UUID of the payment method to delete", required = true)
            @PathVariable String uuid) {
        log.info("REST request to delete payment method with UUID: {}", uuid);

        paymentMethodService.deletePaymentMethod(uuid);

        log.info("Payment method deleted successfully with UUID: {}", uuid);
        return ResponseEntity.ok(APIResponse.success("Payment method deleted successfully", null));
    }

    /**
     * Toggle payment method status (Admin only)
     */
    @PatchMapping("/{uuid}/toggle-status")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(
            summary = "Toggle payment method status",
            description = "Activates or deactivates a payment method. Admin access required.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment method status toggled successfully",
                    content = @Content(schema = @Schema(implementation = PaymentMethodResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Authentication required"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin access required"),
            @ApiResponse(responseCode = "404", description = "Payment method not found")
    })
    public ResponseEntity<APIResponse<PaymentMethodResponse>> togglePaymentMethodStatus(
            @Parameter(description = "UUID of the payment method", required = true)
            @PathVariable String uuid) {
        log.info("REST request to toggle payment method status for UUID: {}", uuid);

        PaymentMethodResponse response = paymentMethodService.togglePaymentMethodStatus(uuid);

        log.info("Payment method status toggled successfully to {} for UUID: {}", response.getIsActive(), uuid);
        return ResponseEntity.ok(APIResponse.success("Payment method status updated successfully", response));
    }

    /**
     * Get payment method by code
     */
    @GetMapping("/code/{code}")
    @Operation(
            summary = "Get payment method by code",
            description = "Retrieves payment method details by unique code. Public access."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment method found",
                    content = @Content(schema = @Schema(implementation = PaymentMethodResponse.class))),
            @ApiResponse(responseCode = "404", description = "Payment method not found")
    })
    public ResponseEntity<APIResponse<PaymentMethodResponse>> getPaymentMethodByCode(
            @Parameter(description = "Unique code of the payment method", required = true, example = "onepay")
            @PathVariable String code) {
        log.debug("REST request to get payment method by code: {}", code);

        PaymentMethodResponse response = paymentMethodService.getPaymentMethodByCode(code);

        return ResponseEntity.ok(APIResponse.success("Payment method retrieved successfully", response));
    }
}
