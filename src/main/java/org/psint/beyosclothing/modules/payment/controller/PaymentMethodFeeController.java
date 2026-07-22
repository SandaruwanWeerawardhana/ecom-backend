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
import org.psint.beyosclothing.modules.payment.dto.request.CreatePaymentMethodFeeRequest;
import org.psint.beyosclothing.modules.payment.dto.request.UpdatePaymentMethodFeeRequest;
import org.psint.beyosclothing.modules.payment.dto.response.PaymentMethodFeeResponse;
import org.psint.beyosclothing.modules.payment.service.PaymentMethodFeeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payment-methods/{paymentMethodUuid}/fees")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payment Method Fees", description = "APIs for managing payment method fees and charges")
public class PaymentMethodFeeController {

    private final PaymentMethodFeeService feeService;

    @PostMapping
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(
            summary = "Add a new fee",
            description = "Adds a new fee to a payment method. Supports percentage-based or fixed fees with min/max limits. Requires ADMIN role.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Fee created successfully",
                    content = @Content(schema = @Schema(implementation = PaymentMethodFeeResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input data (e.g., percentage > 100, minFee > maxFee)"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Authentication required"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin access required"),
            @ApiResponse(responseCode = "404", description = "Payment method not found"),
            @ApiResponse(responseCode = "409", description = "Conflict - Fee already exists for this customer type")
    })
    public ResponseEntity<APIResponse<PaymentMethodFeeResponse>> addFee(
            @Parameter(description = "UUID of the payment method", required = true, example = "abc-123-def")
            @PathVariable String paymentMethodUuid,
            @Valid @RequestBody CreatePaymentMethodFeeRequest request) {
        log.info("REST request to add fee for payment method: {}", paymentMethodUuid);

        // Set payment method UUID in request
        request.setPaymentMethodUuid(paymentMethodUuid);

        PaymentMethodFeeResponse response = feeService.addFee(request);

        log.info("Fee added successfully with ID: {} for payment method: {}", response.getUuid(), paymentMethodUuid);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(APIResponse.created(response));
    }

    @GetMapping
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(
            summary = "Get all fees for a payment method",
            description = "Retrieves all fees configured for a payment method, including inactive fees. Requires ADMIN role.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Fees retrieved successfully",
                    content = @Content(schema = @Schema(implementation = PaymentMethodFeeResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Authentication required"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin access required"),
            @ApiResponse(responseCode = "404", description = "Payment method not found")
    })
    public ResponseEntity<APIResponse<List<PaymentMethodFeeResponse>>> getFees(
            @Parameter(description = "UUID of the payment method", required = true, example = "abc-123-def")
            @PathVariable String paymentMethodUuid) {
        log.debug("REST request to get fees for payment method: {}", paymentMethodUuid);

        List<PaymentMethodFeeResponse> response = feeService.getFeesByMethod(paymentMethodUuid);

        log.debug("Retrieved {} fees for payment method: {}", response.size(), paymentMethodUuid);
        return ResponseEntity.ok(APIResponse.success("Fees retrieved successfully", response));
    }

    @PutMapping("/{feeId}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(
            summary = "Update a fee",
            description = "Updates an existing fee. All fields are optional for partial updates. Requires ADMIN role.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Fee updated successfully",
                    content = @Content(schema = @Schema(implementation = PaymentMethodFeeResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input data (e.g., percentage > 100, minFee > maxFee)"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Authentication required"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin access required"),
            @ApiResponse(responseCode = "404", description = "Fee not found"),
            @ApiResponse(responseCode = "409", description = "Conflict - Fee already exists for the new customer type")
    })
    public ResponseEntity<APIResponse<PaymentMethodFeeResponse>> updateFee(
            @Parameter(description = "UUID of the payment method", required = true)
            @PathVariable String paymentMethodUuid,
            @Parameter(description = "ID of the fee to update", required = true, example = "1")
            @PathVariable Long feeId,
            @Valid @RequestBody UpdatePaymentMethodFeeRequest request) {
        log.info("REST request to update fee with ID: {} for payment method: {}", feeId, paymentMethodUuid);

        PaymentMethodFeeResponse response = feeService.updateFee(feeId, request);

        log.info("Fee updated successfully with ID: {}", feeId);
        return ResponseEntity.ok(APIResponse.success("Fee updated successfully", response));
    }

    @DeleteMapping("/{feeId}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(
            summary = "Delete a fee",
            description = "Soft deletes a fee by setting its isActive flag to false. The fee record is preserved in the database. Requires ADMIN role.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Fee deleted successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Authentication required"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin access required"),
            @ApiResponse(responseCode = "404", description = "Fee not found")
    })
    public ResponseEntity<APIResponse<Void>> deleteFee(
            @Parameter(description = "UUID of the payment method", required = true)
            @PathVariable String paymentMethodUuid,
            @Parameter(description = "ID of the fee to delete", required = true, example = "1")
            @PathVariable Long feeId) {
        log.info("REST request to delete fee with ID: {} for payment method: {}", feeId, paymentMethodUuid);

        feeService.deleteFee(feeId);

        log.info("Fee deleted successfully with ID: {}", feeId);
        return ResponseEntity.ok(APIResponse.success("Fee deleted successfully", null));
    }
}

