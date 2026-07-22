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
import org.psint.beyosclothing.modules.payment.dto.request.CreatePaymentMethodConfigRequest;
import org.psint.beyosclothing.modules.payment.dto.request.UpdatePaymentMethodConfigRequest;
import org.psint.beyosclothing.modules.payment.dto.response.PaymentMethodConfigResponse;
import org.psint.beyosclothing.modules.payment.service.PaymentMethodConfigService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payment-methods/{paymentMethodUuid}/config")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payment Method Configurations", description = "APIs for managing payment method configurations (API keys, secrets)")
public class PaymentMethodConfigController {

    private final PaymentMethodConfigService configService;

    @PostMapping
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(
            summary = "Add a new configuration",
            description = "Adds a new configuration to a payment method. Secret values are encrypted before storage. Requires ADMIN role.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Configuration created successfully",
                    content = @Content(schema = @Schema(implementation = PaymentMethodConfigResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input data"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Authentication required"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin access required"),
            @ApiResponse(responseCode = "404", description = "Payment method not found"),
            @ApiResponse(responseCode = "409", description = "Conflict - Config key already exists")
    })
    public ResponseEntity<APIResponse<PaymentMethodConfigResponse>> addConfig(
            @Parameter(description = "UUID of the payment method", required = true)
            @PathVariable String paymentMethodUuid,
            @Valid @RequestBody CreatePaymentMethodConfigRequest request) {
        log.info("REST request to add config for payment method: {}", paymentMethodUuid);

        PaymentMethodConfigResponse response = configService.addConfig(paymentMethodUuid, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(APIResponse.created(response));
    }

    @GetMapping
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(
            summary = "Get all configurations for a payment method",
            description = "Retrieves all configurations for a payment method. Secret values are masked. Requires ADMIN role.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Configurations retrieved successfully",
                    content = @Content(schema = @Schema(implementation = PaymentMethodConfigResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Authentication required"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin access required"),
            @ApiResponse(responseCode = "404", description = "Payment method not found")
    })
    public ResponseEntity<APIResponse<List<PaymentMethodConfigResponse>>> getConfigs(
            @Parameter(description = "UUID of the payment method", required = true)
            @PathVariable String paymentMethodUuid) {
        log.debug("REST request to get configs for payment method: {}", paymentMethodUuid);

        List<PaymentMethodConfigResponse> response = configService.getConfigs(paymentMethodUuid);

        return ResponseEntity.ok(APIResponse.success("Configurations retrieved successfully", response));
    }

    /**
     * Get a single configuration by ID
     */
    @GetMapping("/{configId}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(
            summary = "Get configuration by ID",
            description = "Retrieves a specific configuration by its ID. Secret values are masked. Requires ADMIN role.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Configuration retrieved successfully",
                    content = @Content(schema = @Schema(implementation = PaymentMethodConfigResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Authentication required"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin access required"),
            @ApiResponse(responseCode = "404", description = "Configuration not found")
    })
    public ResponseEntity<APIResponse<PaymentMethodConfigResponse>> getConfigById(
            @Parameter(description = "UUID of the payment method", required = true)
            @PathVariable String paymentMethodUuid,
            @Parameter(description = "ID of the configuration", required = true)
            @PathVariable Long configId) {
        log.debug("REST request to get config by ID: {}", configId);

        PaymentMethodConfigResponse response = configService.getConfigById(configId);

        return ResponseEntity.ok(APIResponse.success("Configuration retrieved successfully", response));
    }

    /**
     * Update an existing configuration
     */
    @PutMapping("/{configId}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(
            summary = "Update a configuration",
            description = "Updates an existing configuration. Secret values are re-encrypted if changed. Requires ADMIN role.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Configuration updated successfully",
                    content = @Content(schema = @Schema(implementation = PaymentMethodConfigResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input data"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Authentication required"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin access required"),
            @ApiResponse(responseCode = "404", description = "Configuration not found"),
            @ApiResponse(responseCode = "409", description = "Conflict - Config key already exists")
    })
    public ResponseEntity<APIResponse<PaymentMethodConfigResponse>> updateConfig(
            @Parameter(description = "UUID of the payment method", required = true)
            @PathVariable String paymentMethodUuid,
            @Parameter(description = "ID of the configuration to update", required = true)
            @PathVariable Long configId,
            @Valid @RequestBody UpdatePaymentMethodConfigRequest request) {
        log.info("REST request to update config with ID: {}", configId);

        PaymentMethodConfigResponse response = configService.updateConfig(configId, request);

        log.info("Config updated successfully with ID: {}", configId);
        return ResponseEntity.ok(APIResponse.success("Configuration updated successfully", response));
    }

    /**
     * Delete a configuration
     */
    @DeleteMapping("/{configId}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(
            summary = "Delete a configuration",
            description = "Permanently deletes a configuration. Requires ADMIN role.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Configuration deleted successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Authentication required"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin access required"),
            @ApiResponse(responseCode = "404", description = "Configuration not found")
    })
    public ResponseEntity<APIResponse<Void>> deleteConfig(
            @Parameter(description = "UUID of the payment method", required = true)
            @PathVariable String paymentMethodUuid,
            @Parameter(description = "ID of the configuration to delete", required = true)
            @PathVariable Long configId) {
        log.info("REST request to delete config with ID: {}", configId);

        configService.deleteConfig(configId);

        log.info("Config deleted successfully with ID: {}", configId);
        return ResponseEntity.ok(APIResponse.success("Configuration deleted successfully", null));
    }
}

