package org.psint.beyosclothing.modules.promotions.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.common.constants.AppConstants;
import org.psint.beyosclothing.common.dto.APIResponse;
import org.psint.beyosclothing.common.dto.PageResponse;
import org.psint.beyosclothing.modules.promotions.dto.request.CreatePromotionRequest;
import org.psint.beyosclothing.modules.promotions.dto.request.UpdatePromotionRequest;
import org.psint.beyosclothing.modules.promotions.dto.response.PromotionResponse;
import org.psint.beyosclothing.modules.promotions.service.PromotionService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Promotion Controller
 * Manages promotion and discount CRUD operations
 * ADMIN ONLY - Requires specific permissions
 */
@RestController
@RequestMapping(AppConstants.API_VERSION + "/promotions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Promotions", description = "APIs for managing promotions and discounts")
@SecurityRequirement(name = "Bearer Authentication")
public class PromotionController {

    private final PromotionService promotionService;

    /**
     * Create a new promotion
     * Required: ADMIN user type with CREATE_PROMOTION permission
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN') and hasAuthority('CREATE_PROMOTION')")
    @Operation(
            summary = "Create promotion",
            description = "Create a new promotion or discount. " +
                    "Supports: BOGO, percentage discounts, fixed discounts, category-based promotions, product-specific promotions. " +
                    "Requires ADMIN user type with CREATE_PROMOTION permission."
    )
    public ResponseEntity<APIResponse<PromotionResponse>> createPromotion(
            @Valid @RequestBody CreatePromotionRequest request) {
        log.info("POST /api/v1/promotions - Creating promotion: {}", request.getName());

        PromotionResponse response = promotionService.createPromotion(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(APIResponse.<PromotionResponse>builder()
                        .success(true)
                        .message("Promotion created successfully")
                        .data(response)
                        .build());
    }

    /**
     * Update existing promotion
     * Required: ADMIN user type with EDIT_PROMOTION permission
     */
    @PutMapping("/{uuid}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') and hasAuthority('EDIT_PROMOTION')")
    @Operation(
            summary = "Update promotion",
            description = "Update an existing promotion. Requires ADMIN user type with EDIT_PROMOTION permission."
    )
    public ResponseEntity<APIResponse<PromotionResponse>> updatePromotion(
            @PathVariable String uuid,
            @Valid @RequestBody UpdatePromotionRequest request) {
        log.info("PUT /api/v1/promotions/{} - Updating promotion", uuid);

        PromotionResponse response = promotionService.updatePromotion(uuid, request);

        return ResponseEntity.ok(APIResponse.<PromotionResponse>builder()
                .success(true)
                .message("Promotion updated successfully")
                .data(response)
                .build());
    }

    /**
     * Get promotion by UUID
     * Required: ADMIN user type with VIEW_PROMOTION permission
     */
    @GetMapping("/{uuid}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') and hasAuthority('VIEW_PROMOTION')")
    @Operation(
            summary = "Get promotion by UUID",
            description = "Retrieve a specific promotion with all details. Requires ADMIN user type with VIEW_PROMOTION permission."
    )
    public ResponseEntity<APIResponse<PromotionResponse>> getPromotionByUuid(@PathVariable String uuid) {
        log.info("GET /api/v1/promotions/{} - Fetching promotion", uuid);

        PromotionResponse response = promotionService.getPromotionByUuid(uuid);

        return ResponseEntity.ok(APIResponse.<PromotionResponse>builder()
                .success(true)
                .message("Promotion retrieved successfully")
                .data(response)
                .build());
    }

    /**
     * Get promotion by promo code
     * Required: ADMIN user type with VIEW_PROMOTION permission
     */
    @GetMapping("/code/{promoCode}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') and hasAuthority('VIEW_PROMOTION')")
    @Operation(
            summary = "Get promotion by promo code",
            description = "Retrieve a specific promotion by its promo code. Requires ADMIN user type with VIEW_PROMOTION permission."
    )
    public ResponseEntity<APIResponse<PromotionResponse>> getPromotionByCode(@PathVariable String promoCode) {
        log.info("GET /api/v1/promotions/code/{} - Fetching promotion", promoCode);

        PromotionResponse response = promotionService.getPromotionByCode(promoCode);

        return ResponseEntity.ok(APIResponse.<PromotionResponse>builder()
                .success(true)
                .message("Promotion retrieved successfully")
                .data(response)
                .build());
    }

    /**
     * Get all promotions with pagination
     * Required: ADMIN user type with VIEW_PROMOTION permission
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN') and hasAuthority('VIEW_PROMOTION')")
    @Operation(
            summary = "Get all promotions (paginated)",
            description = "Retrieve all promotions with pagination support. Requires ADMIN user type with VIEW_PROMOTION permission."
    )
    public ResponseEntity<APIResponse<PageResponse<PromotionResponse>>> getAllPromotions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "dateCreated") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {
        log.info("GET /api/v1/promotions - Fetching all promotions (page: {}, size: {})", page, size);

        Sort sort = sortDirection.equalsIgnoreCase("DESC")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);
        PageResponse<PromotionResponse> response = promotionService.getAllPromotions(pageable);

        return ResponseEntity.ok(APIResponse.<PageResponse<PromotionResponse>>builder()
                .success(true)
                .message("Promotions retrieved successfully")
                .data(response)
                .build());
    }

    /**
     * Get active promotions
     * Required: ADMIN user type with VIEW_PROMOTION permission
     */
    @GetMapping("/active")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') and hasAuthority('VIEW_PROMOTION')")
    @Operation(
            summary = "Get active promotions",
            description = "Retrieve all currently active promotions. Requires ADMIN user type with VIEW_PROMOTION permission."
    )
    public ResponseEntity<APIResponse<PageResponse<PromotionResponse>>> getActivePromotions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("GET /api/v1/promotions/active - Fetching active promotions");

        Pageable pageable = PageRequest.of(page, size);
        PageResponse<PromotionResponse> response = promotionService.getActivePromotions(pageable);

        return ResponseEntity.ok(APIResponse.<PageResponse<PromotionResponse>>builder()
                .success(true)
                .message("Active promotions retrieved successfully")
                .data(response)
                .build());
    }

    /**
     * Validate promotion code for cart (Public API for customers)
     * Public endpoint - customers can validate promo codes
     */
    @PostMapping("/validate/{promoCode}")
    @Operation(
            summary = "Validate promotion code",
            description = "Validate a promo code for use in cart. Public endpoint - customers can use this to check if a promo code is valid."
    )
    public ResponseEntity<APIResponse<PromotionResponse>> validatePromotionCode(
            @PathVariable String promoCode,
            @RequestParam(required = false) Long customerId) {
        log.info("POST /api/v1/promotions/validate/{} - Validating promo code", promoCode);

        PromotionResponse response = promotionService.validatePromotionCode(promoCode, customerId);

        return ResponseEntity.ok(APIResponse.<PromotionResponse>builder()
                .success(true)
                .message("Promo code is valid")
                .data(response)
                .build());
    }

    /**
     * Delete promotion (soft delete)
     * Required: ADMIN user type with DELETE_PROMOTION permission
     */
    @DeleteMapping("/{uuid}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') and hasAuthority('DELETE_PROMOTION')")
    @Operation(
            summary = "Delete promotion",
            description = "Soft delete a promotion. Requires ADMIN user type with DELETE_PROMOTION permission."
    )
    public ResponseEntity<APIResponse<Void>> deletePromotion(@PathVariable String uuid) {
        log.info("DELETE /api/v1/promotions/{} - Deleting promotion", uuid);

        promotionService.deletePromotion(uuid);

        return ResponseEntity.ok(APIResponse.<Void>builder()
                .success(true)
                .message("Promotion deleted successfully")
                .build());
    }

    /**
     * Activate promotion
     * Required: ADMIN user type with EDIT_PROMOTION permission
     */
    @PatchMapping("/{uuid}/activate")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') and hasAuthority('EDIT_PROMOTION')")
    @Operation(
            summary = "Activate promotion",
            description = "Activate a promotion. Requires ADMIN user type with EDIT_PROMOTION permission."
    )
    public ResponseEntity<APIResponse<PromotionResponse>> activatePromotion(@PathVariable String uuid) {
        log.info("PATCH /api/v1/promotions/{}/activate - Activating promotion", uuid);

        PromotionResponse response = promotionService.activatePromotion(uuid);

        return ResponseEntity.ok(APIResponse.<PromotionResponse>builder()
                .success(true)
                .message("Promotion activated successfully")
                .data(response)
                .build());
    }

    /**
     * Deactivate promotion
     * Required: ADMIN user type with EDIT_PROMOTION permission
     */
    @PatchMapping("/{uuid}/deactivate")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') and hasAuthority('EDIT_PROMOTION')")
    @Operation(
            summary = "Deactivate promotion",
            description = "Deactivate a promotion. Requires ADMIN user type with EDIT_PROMOTION permission."
    )
    public ResponseEntity<APIResponse<PromotionResponse>> deactivatePromotion(@PathVariable String uuid) {
        log.info("PATCH /api/v1/promotions/{}/deactivate - Deactivating promotion", uuid);

        PromotionResponse response = promotionService.deactivatePromotion(uuid);

        return ResponseEntity.ok(APIResponse.<PromotionResponse>builder()
                .success(true)
                .message("Promotion deactivated successfully")
                .data(response)
                .build());
    }
}

