package org.psint.beyosclothing.modules.delivery.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.delivery.dto.request.CalculateShippingCostRequest;
import org.psint.beyosclothing.modules.delivery.dto.request.CreateCourierRateRequest;
import org.psint.beyosclothing.modules.delivery.dto.request.UpdateCourierRateRequest;
import org.psint.beyosclothing.modules.delivery.dto.response.CalculateShippingCostResponse;
import org.psint.beyosclothing.modules.delivery.dto.response.CourierRateResponse;
import org.psint.beyosclothing.modules.delivery.service.CourierService;
import org.psint.beyosclothing.modules.delivery.service.ShippingCostCalculationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/courier-rates")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Courier Rate Controller", description = "APIs for managing courier delivery rates and pricing")
public class CourierRateController {

    private final CourierService courierService;
    private final ShippingCostCalculationService shippingCostCalculationService;

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(
            summary = "Create a new courier rate",
            description = "Creates a new delivery rate for a courier with pricing details. Requires ADMIN permission.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponse(
            responseCode = "201",
            description = "Courier rate created successfully",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = CourierRateResponse.class)
            )
    )
    public ResponseEntity<CourierRateResponse> createCourierRate(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Courier rate creation details",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CreateCourierRateRequest.class)
                    )
            )
            @Valid @RequestBody CreateCourierRateRequest request) {
        log.info("Creating new courier rate for courier UUID: {}", request.getCourierUuid());
        CourierRateResponse response = courierService.createCourierRate(request);
        log.info("Courier rate created successfully with UUID: {}", response.getUuid());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(
            summary = "Update an existing courier rate",
            description = "Updates courier rate information by UUID. All fields in the request body are optional for partial updates. Requires ADMIN permission.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponse(
            responseCode = "200",
            description = "Courier rate updated successfully",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = CourierRateResponse.class)
            )
    )
    public ResponseEntity<CourierRateResponse> updateCourierRate(
            @Parameter(
                    description = "UUID of the courier rate to update",
                    required = true,
                    example = "123e4567-e89b-12d3-a456-426614174000"
            )
            @PathVariable String uuid,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Courier rate update details (all fields optional for partial update)",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UpdateCourierRateRequest.class)
                    )
            )
            @Valid @RequestBody UpdateCourierRateRequest request) {

        CourierRateResponse response = courierService.updateCourierRate(uuid, request);
        log.info("Courier rate updated successfully with UUID: {}", uuid);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{uuid}")
    @Operation(
            summary = "Get courier rate by UUID",
            description = "Retrieves detailed information about a specific courier rate by its UUID. Public access."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Courier rate found and returned successfully",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = CourierRateResponse.class)
            )
    )
    public ResponseEntity<CourierRateResponse> getCourierRateByUuid(
            @Parameter(
                    description = "UUID of the courier rate to retrieve",
                    required = true,
                    example = "123e4567-e89b-12d3-a456-426614174000"
            )
            @PathVariable String uuid) {
        log.info("Fetching courier rate with UUID: {}", uuid);
        CourierRateResponse response = courierService.getCourierRateByUuid(uuid);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<CourierRateResponse>> getCourierRatesByCourier(@RequestParam String courierUuid) {

        List<CourierRateResponse> response = courierService.getCourierRatesByCourier(courierUuid);
        log.info("Retrieved {} courier rates for courier UUID: {}", response.size(), courierUuid);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{uuid}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(
            summary = "Soft delete a courier rate",
            description = "Soft deletes a courier rate by setting its isActive flag to false. The rate record is not physically deleted from the database. Requires ADMIN permission.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponse(
            responseCode = "204",
            description = "Courier rate soft deleted successfully (No Content)"
    )
    public ResponseEntity<Void> deleteCourierRate(
            @Parameter(
                    description = "UUID of the courier rate to delete",
                    required = true,
                    example = "123e4567-e89b-12d3-a456-426614174000"
            )
            @PathVariable String uuid) {
        courierService.deleteCourierRate(uuid);
        log.info("Courier rate soft deleted successfully with UUID: {}", uuid);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{uuid}/toggle-status")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(
            summary = "Toggle courier rate status",
            description = "Activates or deactivates a courier rate by toggling its isActive flag. If currently active, it will be deactivated. If inactive, it will be activated. Requires ADMIN permission.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponse(
            responseCode = "200",
            description = "Courier rate status toggled successfully",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = CourierRateResponse.class)
            )
    )
    public ResponseEntity<CourierRateResponse> toggleCourierRateStatus(
            @Parameter(
                    description = "UUID of the courier rate to toggle status",
                    required = true,
                    example = "123e4567-e89b-12d3-a456-426614174000"
            )
            @PathVariable String uuid) {
        log.info("Toggling status for courier rate with UUID: {}", uuid);
        CourierRateResponse response = courierService.toggleCourierRateStatus(uuid);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/calculate")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(
            summary = "Calculate shipping cost",
            description = "Calculates shipping cost based on total weight, courier, customer type, and payment method. " +
                    "Returns detailed breakdown including applicable rates, granularity calculations, and any applicable min/max charges."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Shipping cost calculated successfully",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = CalculateShippingCostResponse.class)
            )
    )
    @ApiResponse(
            responseCode = "404",
            description = "Courier not found or no applicable rate found"
    )
    @ApiResponse(
            responseCode = "400",
            description = "Invalid request parameters"
    )
    public ResponseEntity<CalculateShippingCostResponse> calculateShippingCost(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Shipping cost calculation request",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CalculateShippingCostRequest.class)
                    )
            )
            @Valid @RequestBody CalculateShippingCostRequest request) {
        log.info("Calculating shipping cost for courier UUID: {}, weight: {}, customerType: {}",
                request.getCourierUuid(), request.getTotalWeight(), request.getCustomerType());

        CalculateShippingCostResponse response = shippingCostCalculationService.calculateShippingCost(request);

        log.info("Shipping cost calculated: {} for weight: {}",
                response.getShippingCost(), request.getTotalWeight());

        return ResponseEntity.ok(response);
    }
}