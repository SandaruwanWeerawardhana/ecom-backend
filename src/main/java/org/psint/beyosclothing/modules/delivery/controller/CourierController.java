package org.psint.beyosclothing.modules.delivery.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.delivery.dto.request.CreateCourierRequest;
import org.psint.beyosclothing.modules.delivery.dto.request.UpdateCourierRequest;
import org.psint.beyosclothing.modules.delivery.dto.response.CourierResponse;
import org.psint.beyosclothing.modules.delivery.dto.response.KoombiyoCityResponse;
import org.psint.beyosclothing.modules.delivery.dto.response.KoombiyoDistrictResponse;
import org.psint.beyosclothing.modules.delivery.dto.response.KoombiyoWaybillResponse;
import org.psint.beyosclothing.modules.delivery.service.CourierService;
import org.psint.beyosclothing.modules.delivery.service.KoombiyoApiService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/couriers")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Courier Controller", description = "APIs for managing courier companies and their configurations")
public class CourierController {

    private final CourierService courierService;
    private final KoombiyoApiService koombiyoApiService;

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(
            summary = "Create a new courier",
            description = "Creates a new courier company with configuration details. Requires ADMIN role. " +
                    "The courier code will be automatically converted to uppercase and trimmed.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    public ResponseEntity<CourierResponse> createCourier(@Valid @RequestBody CreateCourierRequest request) {
        log.info("Creating new courier with code: {}", request.getCode());
        CourierResponse response = courierService.createCourier(request);
        log.info("Courier created successfully with UUID: {}", response.getUuid());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(
            summary = "Update an existing courier",
            description = "Updates courier information by UUID. All fields in the request body are optional for partial updates. " +
                    "Requires ADMIN role. Code and name uniqueness is validated.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )

    public ResponseEntity<CourierResponse> updateCourier(
            @PathVariable String uuid,
            @Valid @RequestBody UpdateCourierRequest request) {
        log.info("Updating courier with UUID: {}", uuid);
        CourierResponse response = courierService.updateCourier(uuid, request);
        log.info("Courier updated successfully with UUID: {}", uuid);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{uuid}")
    @Operation(
            summary = "Get courier by UUID",
            description = "Retrieves detailed information about a specific courier by its UUID. " +
                    "This endpoint is public and does not require authentication."
    )
    public ResponseEntity<CourierResponse> getCourierByUuid(@PathVariable String uuid) {
        log.info("Fetching courier with UUID: {}", uuid);
        CourierResponse response = courierService.getCourierByUuid(uuid);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(
            summary = "Get all active couriers",
            description = "Retrieves a list of all active couriers in the system. " +
                    "Only couriers with isActive=true are returned. " +
                    "This endpoint is public and does not require authentication."
    )
    public ResponseEntity<List<CourierResponse>> getAllActiveCouriers() {
        log.info("Fetching all active couriers");
        List<CourierResponse> response = courierService.getAllActiveCouriers();
        log.info("Retrieved {} active couriers", response.size());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{uuid}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(
            summary = "Soft delete a courier",
            description = "Soft deletes a courier by setting its isActive flag to false. " +
                    "The courier record is not physically deleted from the database. " +
                    "Requires ADMIN role.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    public ResponseEntity<Void> deleteCourier(@PathVariable String uuid) {
        log.info("Soft deleting courier with UUID: {}", uuid);
        courierService.deleteCourier(uuid);
        log.info("Courier soft deleted successfully with UUID: {}", uuid);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{uuid}/toggle-status")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(
            summary = "Toggle courier status",
            description = "Activates or deactivates a courier by toggling its isActive flag. " +
                    "If currently active, it will be deactivated. If inactive, it will be activated. " +
                    "Requires ADMIN role.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    public ResponseEntity<CourierResponse> toggleCourierStatus(
            @Parameter(
                    description = "UUID of the courier to toggle status",
                    required = true,
                    example = "123e4567-e89b-12d3-a456-426614174000"
            )
            @PathVariable String uuid) {
        log.info("Toggling status for courier with UUID: {}", uuid);
        CourierResponse response = courierService.toggleCourierStatus(uuid);
        log.info("Courier status toggled successfully for UUID: {}", uuid);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/koombiyo/districts")
    @Operation(
            summary = "Get all districts from Koombiyo API",
            description = "Calls the Koombiyo external Districts API. The active courier and its API key are " +
                    "resolved automatically from the database — no courier UUID required from the frontend. " +
                    "The raw response is returned as-is and also persisted in the CourierApiLog table for inspection.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    public ResponseEntity<KoombiyoDistrictResponse> getDistricts() {
        log.info("GET /api/v1/couriers/koombiyo/districts - Fetching districts from Koombiyo API");
        KoombiyoDistrictResponse response = koombiyoApiService.getDistricts();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/koombiyo/cities")
    @Operation(
            summary = "Get cities by district from Koombiyo API",
            description = "Calls the Koombiyo external Cities API for the given district ID. The active courier " +
                    "and its API key are resolved automatically from the database — no courier UUID required from " +
                    "the frontend. The raw response is returned as-is and also persisted in the CourierApiLog table.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    public ResponseEntity<KoombiyoCityResponse> getCitiesByDistrict(
            @Parameter(description = "District ID obtained from the Districts API", required = true)
            @RequestParam Integer districtId) {
        log.info("GET /api/v1/couriers/koombiyo/cities?districtId={} - Fetching cities from Koombiyo API", districtId);
        KoombiyoCityResponse response = koombiyoApiService.getCitiesByDistrict(districtId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/koombiyo/waybills")
    @Operation(
            summary = "Get allocated waybill IDs (barcodes) from Koombiyo API",
            description = "Calls the Koombiyo external Waybills (Barcodes) API to retrieve allocated waybill numbers. " +
                    "The active courier and its API key are resolved automatically from the database — no courier UUID " +
                    "required from the frontend. The raw response is returned as-is and also persisted in the CourierApiLog table.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    public ResponseEntity<KoombiyoWaybillResponse> getWaybills(
            @Parameter(description = "Number of waybills to retrieve (default: 1)", required = false)
            @RequestParam(value = "limit", required = false) Integer limit) {
        log.info("GET /api/v1/couriers/koombiyo/waybills?limit={} - Fetching waybills from Koombiyo API", limit);
        KoombiyoWaybillResponse response = koombiyoApiService.getWaybills(limit);
        return ResponseEntity.ok(response);
    }
}
