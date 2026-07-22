package org.psint.beyosclothing.modules.pos.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.common.constants.ResponseCode;
import org.psint.beyosclothing.common.dto.APIResponse;
import org.psint.beyosclothing.modules.pos.dto.response.*;
import org.psint.beyosclothing.modules.pos.dto.response.PosProductListResponse;
import org.psint.beyosclothing.modules.pos.dto.document.PosSearchAnalyticsDocument;
import org.psint.beyosclothing.modules.pos.service.PosProductService;
import org.psint.beyosclothing.modules.pos.service.PosSearchAnalyticsService;
import org.psint.beyosclothing.modules.products.dto.response.ImageThumbnailUrlResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.Parameter;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/pos/products")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "POS Products", description = "POS optimized product search and lookup")
public class PosProductController {

    private final PosProductService productService;
    private final PosSearchAnalyticsService analyticsService;

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN') and hasAuthority('INVENTORY_MANAGE')")
    @Operation(summary = "Get all active products for POS",
            description = "Retrieve paginated list of all active products optimized for POS interface. " +
                    "Returns product details including UUID, title, SKU, thumbnail, prices, and available stock. " +
                    "For VARIABLE products, price is fetched from the default variant. " +
                    "Requires ADMIN role with INVENTORY_MANAGE permission."
    )
    public ResponseEntity<APIResponse<PosProductListResponse>> getAllActiveProducts(
            @Parameter(description = "Page number (0-based)", examples = @ExampleObject(value = "0"))
            @RequestParam(defaultValue = "0") @Min(0) Integer page,

            @Parameter(description = "Page size (max 100)", examples = @ExampleObject(value = "20"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer size
    ) {
        long start = System.nanoTime();
        log.info("GET /api/v1/pos/products - Fetching all active products - Page: {}, Size: {}", page, size);

        PosProductListResponse response = productService.getAllActiveProducts(page, size);

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        log.info("Fetched {} products in {} ms", response.getProducts().size(), elapsedMs);

        return ResponseEntity.ok(APIResponse.<PosProductListResponse>builder()
                .responseCode(ResponseCode.SUCCESS.getCode())
                .success(true)
                .message("Products retrieved successfully")
                .data(response)
                .build());
    }

    @GetMapping("/search")
    @Operation(summary = "Search products for POS autocomplete",
            description = "Fast product search optimized for POS UI. Returns lightweight product info."
    )
    public ResponseEntity<APIResponse<List<PosProductSearchResponse>>> searchProducts(
            @Parameter(description = "Search query (min 2 chars)", required = true,
                    examples = @ExampleObject(value = "shirt"))
            @RequestParam @NotBlank @Size(min = 2, message = "query must be at least 2 characters") String query,

            @Parameter(description = "Maximum results to return (default 20, max 50)", examples = @ExampleObject(value = "20"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) Integer limit,

            @RequestHeader(value = "X-Terminal-UUID", required = false) String terminalId,
            @RequestHeader(value = "X-Cashier-UUID", required = false) String cashierId
    ) {
        long start = System.nanoTime();
        List<PosProductSearchResponse> results = productService.searchProducts(query, limit);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        log.debug("Pos search query='{}' limit={} returned {} results in {} ms", query, limit, results.size(), elapsedMs);

        // Log analytics asynchronously (fire-and-forget)
        PosSearchAnalyticsDocument doc = PosSearchAnalyticsDocument.builder()
                .searchTerm(query)
                .timestamp(LocalDateTime.now())
                .terminalId(terminalId)
                .cashierId(cashierId)
                .resultsCount(results.size())
                .selectedProductId(null)
                .build();
        analyticsService.logSearch(doc);

        return ResponseEntity.ok(APIResponse.<List<PosProductSearchResponse>>builder()
                .responseCode(ResponseCode.SUCCESS.getCode())
                .success(true)
                .message("Products retrieved")
                .data(results)
                .build());
    }

    @GetMapping("/{productId}")
    @Operation(summary = "Get product details for POS",
            description = "Returns product details including variants and stock availability."
    )
    public ResponseEntity<APIResponse<PosProductSearchResponse>> getProductById(@PathVariable Long productId) {
        long start = System.nanoTime();
        PosProductSearchResponse resp = productService.getProductForPos(productId);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        log.debug("getProductById id={} elapsedMs={}", productId, elapsedMs);
        return ResponseEntity.ok(APIResponse.<PosProductSearchResponse>builder()
                .responseCode(ResponseCode.SUCCESS.getCode())
                .success(true)
                .message("Product retrieved")
                .data(resp)
                .build());
    }

    @PostMapping("/analytics/convert")
    public ResponseEntity<APIResponse<Void>> recordConversion(
            @RequestParam String searchTerm,
            @RequestParam String selectedProductId,
            @RequestHeader(value = "X-Terminal-UUID", required = false) String terminalId,
            @RequestHeader(value = "X-Cashier-UUID", required = false) String cashierId
    ) {
        PosSearchAnalyticsDocument doc = PosSearchAnalyticsDocument.builder()
                .searchTerm(searchTerm)
                .timestamp(LocalDateTime.now())
                .terminalId(terminalId)
                .cashierId(cashierId)
                .resultsCount(null)
                .selectedProductId(selectedProductId)
                .build();
        analyticsService.logSearch(doc);
        return ResponseEntity.ok(APIResponse.<Void>builder()
                .responseCode(ResponseCode.SUCCESS.getCode())
                .success(true)
                .message("Conversion recorded")
                .data(null)
                .build());
    }

    @GetMapping("/details/{uuid}")
    @Operation(summary = "Get product details for POS by UUID",
            description = "Returns detailed product information including variants and stock availability."
    )
    public ResponseEntity<APIResponse<PosProductPopupResponse>> getProductuuid(@PathVariable String uuid) {
        log.info("API: POS getProductByUuid - uuid={}", uuid);
        try {
            PosProductPopupResponse popup = productService.getProductForPosByUuid(uuid);
            if (popup == null) {
                return ResponseEntity.status(404).body(APIResponse.error(ResponseCode.NOT_FOUND, "Product not found"));
            }


            return ResponseEntity.ok(APIResponse.<PosProductPopupResponse>builder()
                    .responseCode(ResponseCode.SUCCESS.getCode())
                    .success(true)
                    .message("Product retrieved successfully")
                    .data(popup)
                    .build());
        } catch (Exception e) {
            log.error("Error fetching product by uuid={}", uuid, e);
            return ResponseEntity.status(500).body(APIResponse.error(ResponseCode.INTERNAL_ERROR, "Error retrieving product"));
        }
    }
    /**
     * Get variant gallery images for a product filtered by color, size and product type
     * Public endpoint
     */
    @GetMapping("/variants/gallery/{uuid}")
    @Operation(
            summary = "Get variant gallery images",
            description = "Retrieve variant gallery images for a product filtered by color, size, and product type. " +
                    "All filter parameters are optional. Public endpoint - no authentication required."
    )
    public ResponseEntity<APIResponse<ImageThumbnailUrlPosResponse>> getVariantGalleryImages(@PathVariable String uuid) {

        log.info("GET /api/v1/products/{}/variants/gallery - Fetching variant gallery images", uuid);

        ImageThumbnailUrlPosResponse images = productService.getVariantGalleryImages(uuid);

        return ResponseEntity.ok(APIResponse.<ImageThumbnailUrlPosResponse>builder()
                .success(true)
                .message("Variant gallery images retrieved successfully")
                .data(images)
                .build());

    }


}
