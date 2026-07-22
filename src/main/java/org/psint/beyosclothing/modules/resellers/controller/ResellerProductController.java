package org.psint.beyosclothing.modules.resellers.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.common.dto.APIResponse;
import org.psint.beyosclothing.modules.resellers.dto.response.ResellerProductDetailResponse;
import org.psint.beyosclothing.modules.resellers.dto.response.ResellerProductFullDetailResponse;
import org.psint.beyosclothing.modules.resellers.dto.response.ResellerProductListResponse;
import org.psint.beyosclothing.modules.resellers.dto.response.ResellerVariantPricingResponse;
import org.psint.beyosclothing.modules.resellers.dto.response.ResellerVariantThumbnailResponse;
import org.psint.beyosclothing.modules.resellers.service.ResellerProductService;
import org.psint.beyosclothing.modules.resellers.service.ResellerSecurityService;
import org.psint.beyosclothing.modules.resellers.service.ResellerService;
import org.psint.beyosclothing.modules.resellers.util.JwtUtil;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for Reseller Product Browsing
 * Allows approved resellers to view products with reseller pricing
 */
@RestController
@RequestMapping("/api/v1/resellers/products")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Reseller Products", description = "Product browsing with reseller pricing for approved resellers")
public class ResellerProductController {

    private final ResellerProductService productService;
    private final ResellerSecurityService securityService;
    private final ResellerService resellerService;
    private final JwtUtil jwtUtil;

    /**
     * Get paginated products with reseller pricing
     */
    @GetMapping
    @PreAuthorize("hasRole('RESELLER')")
    @Operation(summary = "Get products with reseller pricing",
               description = "Returns paginated list of products showing both customer price and reseller base price. Requires APPROVED status.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Products retrieved successfully",
                    content = @Content(schema = @Schema(implementation = ResellerProductListResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden - reseller not approved")
    })
    public ResponseEntity<ResellerProductListResponse> getProducts(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category) {
        Long userId = jwtUtil.getUserId(authentication);
        securityService.checkApprovedStatus(userId);
        Long resellerId = resellerService.getResellerByUserId(userId).getId();

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "dateCreated"));

        log.info("Fetching products for reseller ID: {} - Page: {}, Size: {}, Search: {}",
                resellerId, page, size, search);

        ResellerProductListResponse response = productService.getProducts(
                resellerId, search, category, pageable);

        return ResponseEntity.ok(response);
    }

    /**
     * Get single product details by UUID
     */
    @GetMapping("/{productUuid}")
    @PreAuthorize("hasRole('RESELLER')")
    @Operation(summary = "Get product details",
               description = "Returns complete product information including all variants with reseller pricing")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Product details retrieved successfully",
                    content = @Content(schema = @Schema(implementation = ResellerProductDetailResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden - reseller not approved"),
        @ApiResponse(responseCode = "404", description = "Product not found")
    })
    public ResponseEntity<ResellerProductDetailResponse> getProductByUuid(
            Authentication authentication,
            @PathVariable String productUuid) {

        Long userId = jwtUtil.getUserId(authentication);
        securityService.checkApprovedStatus(userId);
        Long resellerId = resellerService.getResellerByUserId(userId).getId();

        log.info("Fetching product details for UUID: {} by reseller ID: {}", productUuid, resellerId);

        ResellerProductDetailResponse response = productService.getProductByUuid(resellerId, productUuid);

        return ResponseEntity.ok(response);
    }

    /**
     * Get variant pricing details
     */
    @GetMapping("/{productUuid}/variants/{variantUuid}/pricing")
    @PreAuthorize("hasRole('RESELLER')")
    @Operation(summary = "Get variant pricing",
               description = "Returns detailed pricing information for a specific variant including allowed markup range")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Variant pricing retrieved successfully",
                    content = @Content(schema = @Schema(implementation = ResellerVariantPricingResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden - reseller not approved"),
        @ApiResponse(responseCode = "404", description = "Product or variant not found")
    })
    public ResponseEntity<ResellerVariantPricingResponse> getVariantPricing(
            Authentication authentication,
            @PathVariable String productUuid,
            @PathVariable String variantUuid) {

        Long userId = jwtUtil.getUserId(authentication);
        securityService.checkApprovedStatus(userId);
        Long resellerId = resellerService.getResellerByUserId(userId).getId();

        log.info("Fetching variant pricing for product: {}, variant: {} by reseller ID: {}",
                productUuid, variantUuid, resellerId);

        ResellerVariantPricingResponse response = productService.getVariantPricing(
                resellerId, productUuid, variantUuid);

        return ResponseEntity.ok(response);
    }

    /**
     * Search products
     */
    @PostMapping("/search")
    @PreAuthorize("hasRole('RESELLER')")
    @Operation(summary = "Advanced product search",
               description = "Search products with advanced filters including price range, category, availability")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Search results retrieved successfully",
                    content = @Content(schema = @Schema(implementation = ResellerProductListResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden - reseller not approved")
    })
    public ResponseEntity<ResellerProductListResponse> searchProducts(
            Authentication authentication,
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Long userId = jwtUtil.getUserId(authentication);
        securityService.checkApprovedStatus(userId);
        Long resellerId = resellerService.getResellerByUserId(userId).getId();

        Pageable pageable = PageRequest.of(page, size);

        log.info("Product search by reseller ID: {} - Keyword: {}", resellerId, keyword);

        // Call the service with keyword and pageable
        ResellerProductListResponse response = productService.getProducts(
                resellerId, keyword, null, pageable);

        return ResponseEntity.ok(response);
    }

    /**
     * Get full product detail by UUID via RabbitMQ cross-module lookup.
     * Mirrors ProductController.getProductBySlug() response structure.
     * No direct cross-module repository dependency.
     */
    @GetMapping("/{productUuid}/detail")
    @PreAuthorize("hasRole('RESELLER')")
    @Operation(
            summary = "Get full product detail (via RabbitMQ)",
            description = "Returns complete product detail including all variants, gallery, meta, dimensions, tags, " +
                    "and reseller markup rules. Data is fetched from Product module via RabbitMQ — " +
                    "no cross-module repository dependency. Requires APPROVED reseller status."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Product detail retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden - reseller not approved"),
        @ApiResponse(responseCode = "404", description = "Product not found")
    })
    public ResponseEntity<APIResponse<ResellerProductFullDetailResponse>> getProductFullDetail(
            Authentication authentication,
            @PathVariable String productUuid) {

        Long userId = jwtUtil.getUserId(authentication);
        securityService.checkApprovedStatus(userId);
        Long resellerId = resellerService.getResellerByUserId(userId).getId();

        log.info("GET /api/v1/resellers/products/{}/detail - Fetching full product detail for reseller ID: {}",
                productUuid, resellerId);

        ResellerProductFullDetailResponse response = productService.getProductFullDetailByUuid(resellerId, productUuid);

        return ResponseEntity.ok(APIResponse.<ResellerProductFullDetailResponse>builder()
                .success(true)
                .message("Product detail retrieved successfully")
                .data(response)
                .build());
    }

    /**
     * Get variant thumbnail URL via RabbitMQ cross-module lookup.
     * Mirrors ProductController.getVariantGalleryImages() response structure.
     * No direct cross-module repository dependency.
     */
    @GetMapping("/variants/thumbnail/{variantUuid}")
    @PreAuthorize("hasRole('RESELLER')")
    @Operation(
            summary = "Get variant thumbnail URL (via RabbitMQ)",
            description = "Returns the thumbnail URL for a specific variant. " +
                    "Data is fetched from Product module via RabbitMQ — " +
                    "no cross-module repository dependency. Requires APPROVED reseller status."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Variant thumbnail retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden - reseller not approved"),
        @ApiResponse(responseCode = "404", description = "Variant not found")
    })
    public ResponseEntity<APIResponse<ResellerVariantThumbnailResponse>> getVariantThumbnail(
            Authentication authentication,
            @PathVariable String variantUuid) {

        Long userId = jwtUtil.getUserId(authentication);
        securityService.checkApprovedStatus(userId);

        log.info("GET /api/v1/resellers/products/variants/thumbnail/{} - Fetching variant thumbnail", variantUuid);

        ResellerVariantThumbnailResponse response = productService.getVariantThumbnail(variantUuid);

        return ResponseEntity.ok(APIResponse.<ResellerVariantThumbnailResponse>builder()
                .success(true)
                .message("Variant thumbnail retrieved successfully")
                .data(response)
                .build());
    }

    /**
     * Product search request DTO
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @Schema(description = "Product search criteria")
    public static class ProductSearchRequest {
        @Schema(description = "Search query", example = "shirt")
        private String query;

        @Schema(description = "Category UUID")
        private String categoryUuid;

        @Schema(description = "Minimum price")
        private java.math.BigDecimal minPrice;

        @Schema(description = "Maximum price")
        private java.math.BigDecimal maxPrice;

        @Schema(description = "Only show available products", example = "true")
        private Boolean availableOnly;

        @Schema(description = "Product type: SIMPLE or VARIABLE")
        private String productType;
    }
}
