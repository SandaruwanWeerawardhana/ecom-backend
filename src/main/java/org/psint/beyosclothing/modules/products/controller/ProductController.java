package org.psint.beyosclothing.modules.products.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.common.constants.AppConstants;
import org.psint.beyosclothing.common.dto.APIResponse;
import org.psint.beyosclothing.common.dto.PageResponse;
import org.psint.beyosclothing.modules.products.dto.request.CreateProductRequest;
import org.psint.beyosclothing.modules.products.dto.request.ShopFilterRequest;
import org.psint.beyosclothing.modules.products.dto.request.UpdateProductRequest;
import org.psint.beyosclothing.modules.products.dto.response.*;
import org.psint.beyosclothing.modules.products.service.ProductService;
import org.psint.beyosclothing.modules.products.service.ProductSearchService;
import org.psint.beyosclothing.modules.products.service.ShopProductService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Product Controller
 * Manages product CRUD operations
 */
@RestController
@RequestMapping(AppConstants.API_VERSION + "/products")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Products", description = "APIs for managing products")
@SecurityRequirement(name = "Bearer Authentication")
public class ProductController {

    private final ProductService productService;
    private final ProductSearchService productSearchService;
    private final ShopProductService shopProductService;

    /**
     * Create a new product
     * Required: ADMIN user type with CREATE_PRODUCT permission
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN') and hasAuthority('CREATE_PRODUCT')")
    @Operation(
            summary = "Create product",
            description = "Create a new product with variants, gallery, meta, dimensions, and tags. " +
                    "Requires ADMIN user type with CREATE_PRODUCT permission. " +
                    "Images must be uploaded first using the image upload APIs."
    )
    public ResponseEntity<APIResponse<ProductResponse>> createProduct(
            @Valid @RequestBody CreateProductRequest request) {
        log.info("POST /api/v1/products - Creating product: {}", request.getTitle());

        ProductResponse response = productService.createProduct(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(APIResponse.<ProductResponse>builder()
                        .success(true)
                        .message("Product created successfully")
                        .data(response)
                        .build());
    }

    /**
     * Update existing product
     * Required: ADMIN user type with EDIT_PRODUCT permission
     */
    @PutMapping("/{uuid}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') and hasAuthority('EDIT_PRODUCT')")
    @Operation(
            summary = "Update product",
            description = "Update an existing product. Requires ADMIN user type with EDIT_PRODUCT permission."
    )
    public ResponseEntity<APIResponse<ProductResponse>> updateProduct(
            @PathVariable String uuid,
            @Valid @RequestBody UpdateProductRequest request) {
        log.info("PUT /api/v1/products/{} - Updating product", uuid);

        ProductResponse response = productService.updateProduct(uuid, request);

        return ResponseEntity.ok(APIResponse.<ProductResponse>builder()
                .success(true)
                .message("Product updated successfully")
                .data(response)
                .build());
    }

    /**
     * Get product by UUID
     * Public endpoint
     */
    @GetMapping("/{uuid}")
    @Operation(
            summary = "Get product by UUID",
            description = "Retrieve a specific product with all details. Public endpoint - no authentication required."
    )
    public ResponseEntity<APIResponse<ProductResponse>> getProductByUuid(@PathVariable String uuid) {
        log.info("GET /api/v1/products/{} - Fetching product", uuid);

        ProductResponse response = productService.getProductByUuid(uuid);

        return ResponseEntity.ok(APIResponse.<ProductResponse>builder()
                .success(true)
                .message("Product retrieved successfully")
                .data(response)
                .build());
    }

    /**
     * Get product by slug
     * Public endpoint
     */
    @GetMapping("/slug/{slug}")
    @Operation(
            summary = "Get product by slug",
            description = "Retrieve a specific product by its URL slug. Public endpoint - no authentication required."
    )
    public ResponseEntity<APIResponse<ProductResponse>> getProductBySlug(@PathVariable String slug) {
        log.info("GET /api/v1/products/slug/{} - Fetching product", slug);

        ProductResponse response = productService.getProductBySlug(slug);

        return ResponseEntity.ok(APIResponse.<ProductResponse>builder()
                .success(true)
                .message("Product retrieved successfully")
                .data(response)
                .build());
    }

    /**
     * Get all products with pagination
     * Public endpoint
     */
    @GetMapping
    @Operation(
            summary = "Get all products (paginated)",
            description = "Retrieve all products with pagination support. Public endpoint - no authentication required."
    )
    public ResponseEntity<APIResponse<PageResponse<ProductResponse>>> getAllProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "dateCreated") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {
        log.info("GET /api/v1/products - Fetching all products (page: {}, size: {})", page, size);

        Sort sort = sortDirection.equalsIgnoreCase("DESC")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);
        PageResponse<ProductResponse> response = productService.getAllProducts(pageable);

        return ResponseEntity.ok(APIResponse.<PageResponse<ProductResponse>>builder()
                .success(true)
                .message("Products retrieved successfully")
                .data(response)
                .build());
    }

    @GetMapping("/filter")
    @Operation(
            summary = "Get all products for filter (optimized response)",
            description = "Retrieve all published products with optimized format including colors, sizes, ratings, and stock info. " +
                    "Returns ProductFilterResponse format. Public endpoint - no authentication required."
    )
    public ResponseEntity<APIResponse<PageResponse<ProductFilterResponse>>> getAllProductsFilter(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "dateCreated") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {
        log.info("GET /api/v1/products/filter - Fetching all products for filter (page: {}, size: {})", page, size);

        Sort sort = sortDirection.equalsIgnoreCase("DESC")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);
        PageResponse<ProductFilterResponse> response = productService.getAllProductsForFilter(pageable);

        return ResponseEntity.ok(APIResponse.<PageResponse<ProductFilterResponse>>builder()
                .success(true)
                .message("Products retrieved successfully")
                .data(response)
                .build());
    }


    /**
     * Get all published products
     * Public endpoint
     */
    @GetMapping("/published")
    @Operation(
            summary = "Get published products",
            description = "Retrieve all published products. Public endpoint - no authentication required."
    )
    public ResponseEntity<APIResponse<PageResponse<ProductResponse>>> getPublishedProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "dateCreated") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {
        log.info("GET /api/v1/products/published - Fetching published products");

        Sort sort = sortDirection.equalsIgnoreCase("DESC")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);
        PageResponse<ProductResponse> response = productService.getPublishedProducts(pageable);

        return ResponseEntity.ok(APIResponse.<PageResponse<ProductResponse>>builder()
                .success(true)
                .message("Published products retrieved successfully")
                .data(response)
                .build());
    }

    /**
     * Get published product cards for website display (optimized for scrolling)
     * Public endpoint - returns minimal product information with ratings and discounts
     * This endpoint is cached for better performance during infinite scrolling
     */
    @GetMapping("/cards")
    @Operation(
            summary = "Get published product cards",
            description = "Retrieve published products as cards with minimal information optimized for website display. " +
                    "Includes product name, image, price, discount, and ratings. " +
                    "This endpoint is cached for better performance during infinite scrolling. " +
                    "Public endpoint - no authentication required."
    )
    public ResponseEntity<APIResponse<PageResponse<ProductCardResponse>>> getPublishedProductCards(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "dateCreated") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {
        log.info("GET /api/v1/products/cards - Fetching published product cards");

        Sort sort = sortDirection.equalsIgnoreCase("DESC")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);
        PageResponse<ProductCardResponse> response = productService.getPublishedProductCards(pageable);

        return ResponseEntity.ok(APIResponse.<PageResponse<ProductCardResponse>>builder()
                .success(true)
                .message("Product cards retrieved successfully")
                .data(response)
                .build());
    }

    /**
     * Get featured products
     * Public endpoint
     */
    @GetMapping("/featured")
    @Operation(
            summary = "Get featured products",
            description = "Retrieve all featured products. Public endpoint - no authentication required."
    )
    public ResponseEntity<APIResponse<PageResponse<ProductResponse>>> getFeaturedProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("GET /api/v1/products/featured - Fetching featured products");

        Pageable pageable = PageRequest.of(page, size);
        PageResponse<ProductResponse> response = productService.getFeaturedProducts(pageable);

        return ResponseEntity.ok(APIResponse.<PageResponse<ProductResponse>>builder()
                .success(true)
                .message("Featured products retrieved successfully")
                .data(response)
                .build());
    }

    /**
     * Get products by category
     * Public endpoint
     */
    @GetMapping("/category/{categoryUuid}")
    @Operation(
            summary = "Get products by category",
            description = "Retrieve all products in a specific category. Public endpoint - no authentication required."
    )
    public ResponseEntity<APIResponse<PageResponse<ProductResponse>>> getProductsByCategory(
            @PathVariable String categoryUuid,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "dateCreated") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {
        log.info("GET /api/v1/products/category/{} - Fetching products", categoryUuid);

        Sort sort = sortDirection.equalsIgnoreCase("DESC")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);
        PageResponse<ProductResponse> response = productService.getProductsByCategory(categoryUuid, pageable);

        return ResponseEntity.ok(APIResponse.<PageResponse<ProductResponse>>builder()
                .success(true)
                .message("Products retrieved successfully")
                .data(response)
                .build());
    }

    /**
     * Delete product (soft delete)
     * Required: ADMIN user type with DELETE_PRODUCT permission
     */
    @DeleteMapping("/{uuid}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') and hasAuthority('DELETE_PRODUCT')")
    @Operation(
            summary = "Delete product",
            description = "Soft delete a product. Requires ADMIN user type with DELETE_PRODUCT permission."
    )
    public ResponseEntity<APIResponse<Void>> deleteProduct(@PathVariable String uuid) {
        log.info("DELETE /api/v1/products/{} - Deleting product", uuid);

        productService.deleteProduct(uuid);

        return ResponseEntity.ok(APIResponse.<Void>builder()
                .success(true)
                .message("Product deleted successfully")
                .build());
    }

    /**
     * Publish product
     * Required: ADMIN user type with EDIT_PRODUCT permission
     */
    @PatchMapping("/{uuid}/publish")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') and hasAuthority('EDIT_PRODUCT')")
    @Operation(
            summary = "Publish product",
            description = "Publish a product to make it visible to customers. Requires ADMIN user type with EDIT_PRODUCT permission."
    )
    public ResponseEntity<APIResponse<ProductResponse>> publishProduct(@PathVariable String uuid) {
        log.info("PATCH /api/v1/products/{}/publish - Publishing product", uuid);

        ProductResponse response = productService.publishProduct(uuid);

        return ResponseEntity.ok(APIResponse.<ProductResponse>builder()
                .success(true)
                .message("Product published successfully")
                .data(response)
                .build());
    }

    /**
     * Unpublish product
     * Required: ADMIN user type with EDIT_PRODUCT permission
     */
    @PatchMapping("/{uuid}/unpublish")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') and hasAuthority('EDIT_PRODUCT')")
    @Operation(
            summary = "Unpublish product",
            description = "Unpublish a product to hide it from customers. Requires ADMIN user type with EDIT_PRODUCT permission."
    )
    public ResponseEntity<APIResponse<ProductResponse>> unpublishProduct(@PathVariable String uuid) {
        log.info("PATCH /api/v1/products/{}/unpublish - Unpublishing product", uuid);

        ProductResponse response = productService.unpublishProduct(uuid);

        return ResponseEntity.ok(APIResponse.<ProductResponse>builder()
                .success(true)
                .message("Product unpublished successfully")
                .data(response)
                .build());
    }

    /**
     * Unified Real-time Product Search API with Autocomplete
     * Combines fuzzy search and prefix matching in one endpoint
     * Public endpoint - no authentication required
     * Features:
     * - Real-time search with fuzzy matching (default mode)
     * - Autocomplete with prefix matching (mode=autocomplete)
     * - Optimized for large datasets (1M+ products)
     * - Returns lightweight results for fast UI rendering
     *
     * @param query Search query string (required)
     * @param limit Maximum results (default: 10, max: 50)
     * @param mode Search mode: "search" (default) or "autocomplete"
     */
    @GetMapping("/public/search")
    @Operation(
            summary = "Unified product search with autocomplete",
            description = "Smart search endpoint supporting both real-time search and autocomplete. " +
                    "**Default mode (search):** Uses fuzzy matching, spelling tolerance, relevance scoring. " +
                    "Example: 'ipone' → 'iPhone 14 Pro'. " +
                    "**Autocomplete mode:** Uses prefix matching for 'as-you-type' suggestions. " +
                    "Example: 'iph' → 'iPhone...' " +
                    "Public endpoint - no authentication required."
    )
    public ResponseEntity<APIResponse<List<SearchResponse>>> searchProducts(
            @RequestParam(name = "q") String query,
            @RequestParam(defaultValue = "10") Integer limit,
            @RequestParam(defaultValue = "search") String mode) {

        log.info("GET /api/v1/products/public/search - Query: '{}', Mode: '{}', Limit: {}",
                query, mode, limit);

        List<SearchResponse> results;

        // Handle autocomplete mode
        if ("autocomplete".equalsIgnoreCase(mode)) {
            results = productSearchService.autocomplete(query, limit);
            log.info("Autocomplete completed: {} results", results == null ? 0 : results.size());
        }
        // Default: fuzzy search mode
        else {
            results = productSearchService.searchProducts(query, limit);
            log.info("Search completed: {} results", results == null ? 0 : results.size());
        }

        // Handle null results
        if (results == null) {
            results = List.of();
        }

        return ResponseEntity.ok(APIResponse.<List<SearchResponse>>builder()
                .success(true)
                .message("Search completed successfully")
                .data(results)
                .build());
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
    public ResponseEntity<APIResponse<ImageThumbnailUrlResponse>> getVariantGalleryImages(@PathVariable String uuid) {

        log.info("GET /api/v1/products/{}/variants/gallery - Fetching variant gallery images", uuid);

        ImageThumbnailUrlResponse images = productService.getVariantGalleryImages(uuid);

        return ResponseEntity.ok(APIResponse.<ImageThumbnailUrlResponse>builder()
                .success(true)
                .message("Variant gallery images retrieved successfully")
                .data(images)
                .build());

    }

    /**
     * Get filtered shop products with pagination
     * Public endpoint - Optimized for shop page display
     *
     * Supports filtering by:
     * - Category (main or sub-category)
     * - Price range (min/max)
     * - Attributes (multiple attribute values)
     * - Stock status (in stock only)
     *
     * Returns product with discount percentage from active promotions
     */
    @PostMapping("/shop/filter")
    @Operation(
            summary = "Get shop products with filters and metadata",
            description = "Retrieve paginated products for shop page with advanced filtering. " +
                    "Filter by category, price range, attributes, and stock status. " +
                    "Returns products with discount percentage from active promotions. " +
                    "Also returns filter metadata for sidebar (categories, subcategories, attributes, price range). " +
                    "Public endpoint - no authentication required."
    )
    public ResponseEntity<APIResponse<ShopPageResponse>> getShopProducts(
            @Valid @RequestBody ShopFilterRequest filterRequest) {

        log.info("POST /api/v1/products/shop/filter - Processing shop filter request");

        ShopPageResponse response = shopProductService.getShopProductsWithMetadata(filterRequest);

        return ResponseEntity.ok(APIResponse.<ShopPageResponse>builder()
                .success(true)
                .message("Shop products retrieved successfully")
                .data(response)
                .build());
    }

}
