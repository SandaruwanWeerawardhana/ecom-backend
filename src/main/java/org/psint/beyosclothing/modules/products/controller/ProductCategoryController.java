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
import org.psint.beyosclothing.modules.products.dto.request.CreateCategoryRequest;
import org.psint.beyosclothing.modules.products.dto.request.UpdateCategoryRequest;
import org.psint.beyosclothing.modules.products.dto.response.CategoryResponse;
import org.psint.beyosclothing.modules.products.dto.response.SubCategoryResponse;
import org.psint.beyosclothing.modules.products.service.ProductCategoryService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Product Category Controller
 * Manages product category operations
 * All write operations require ADMIN user type with specific permissions
 */
@RestController
@RequestMapping(AppConstants.API_VERSION + "/products/categories")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Product Categories", description = "APIs for managing product categories (main categories and sub-categories)")
@SecurityRequirement(name = "Bearer Authentication")
public class ProductCategoryController {

    private final ProductCategoryService categoryService;

    /**
     * Create a new product category (main or sub-category)
     * Required: ADMIN user type with CREATE_PRODUCT permission
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('CREATE_PRODUCT')")
    @Operation(
            summary = "Create category",
            description = "Create a new product category (main category or sub-category). Requires ADMIN user type with CREATE_PRODUCT permission."
    )
    public ResponseEntity<APIResponse<CategoryResponse>> createCategory(
            @Valid @RequestBody CreateCategoryRequest request) {
        log.info("POST /api/v1/products/categories - Creating category: {}", request.getName());

        CategoryResponse response = categoryService.createCategory(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(APIResponse.<CategoryResponse>builder()
                        .success(true)
                        .message("Category created successfully")
                        .data(response)
                        .build());
    }

    /**
     * Get category by UUID
     */
    @GetMapping("/{uuid}")
    @Operation(
            summary = "Get category by UUID",
            description = "Retrieve a specific category by its UUID. Public endpoint - no authentication required."
    )
    public ResponseEntity<APIResponse<CategoryResponse>> getCategoryByUuid(@PathVariable String uuid) {
        log.info("GET /api/v1/products/categories/{} - Fetching category", uuid);

        CategoryResponse response = categoryService.getCategoryByUuid(uuid);

        return ResponseEntity.ok(APIResponse.<CategoryResponse>builder()
                .success(true)
                .message("Category retrieved successfully")
                .data(response)
                .build());
    }

    /**
     * Get all main categories (no parent)
     */
    @GetMapping("/main")
    @Operation(
            summary = "Get main categories",
            description = "Retrieve all main categories (categories without a parent). Public endpoint - no authentication required."
    )
    public ResponseEntity<APIResponse<List<CategoryResponse>>> getMainCategories(
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        log.info("GET /api/v1/products/categories/main - Fetching main categories (activeOnly: {})", activeOnly);

        List<CategoryResponse> response = categoryService.getMainCategories(activeOnly);

        return ResponseEntity.ok(APIResponse.<List<CategoryResponse>>builder()
                .success(true)
                .message("Main categories retrieved successfully")
                .data(response)
                .build());
    }

    /**
     * Get sub-categories of a parent category
     */
    @GetMapping("/{parentUuid}/subcategories")
    @Operation(
            summary = "Get sub-categories",
            description = "Retrieve all sub-categories of a specific parent category. Public endpoint - no authentication required."
    )
    public ResponseEntity<APIResponse<List<CategoryResponse>>> getSubCategories(
            @PathVariable String parentUuid,
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        log.info("GET /api/v1/products/categories/{}/subcategories - Fetching sub-categories", parentUuid);

        List<CategoryResponse> response = categoryService.getSubCategories(parentUuid, activeOnly);

        return ResponseEntity.ok(APIResponse.<List<CategoryResponse>>builder()
                .success(true)
                .message("Sub-categories retrieved successfully")
                .data(response)
                .build());
    }

    /**
     * Get all categories with pagination
     */
    @GetMapping
    @Operation(
            summary = "Get all categories (paginated)",
            description = "Retrieve all categories with pagination support. Public endpoint - no authentication required."
    )
    public ResponseEntity<APIResponse<PageResponse<CategoryResponse>>> getAllCategories(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDirection) {
        log.info("GET /api/v1/products/categories - Fetching all categories (page: {}, size: {})", page, size);

        Sort sort = sortDirection.equalsIgnoreCase("DESC")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);
        PageResponse<CategoryResponse> response = categoryService.getAllCategories(pageable);

        return ResponseEntity.ok(APIResponse.<PageResponse<CategoryResponse>>builder()
                .success(true)
                .message("Categories retrieved successfully")
                .data(response)
                .build());
    }

    /**
     * Get category hierarchy (main categories with their sub-categories)
     */
    @GetMapping("/hierarchy")
    @Operation(
            summary = "Get category hierarchy",
            description = "Retrieve all main categories with their sub-categories in a nested structure. Public endpoint - no authentication required."
    )
    public ResponseEntity<APIResponse<List<CategoryResponse>>> getCategoryHierarchy(
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        log.info("GET /api/v1/products/categories/hierarchy - Fetching category hierarchy");

        List<CategoryResponse> response = categoryService.getCategoryHierarchy(activeOnly);

        return ResponseEntity.ok(APIResponse.<List<CategoryResponse>>builder()
                .success(true)
                .message("Category hierarchy retrieved successfully")
                .data(response)
                .build());
    }

    @GetMapping("subcategories/hierarchy")
    @Operation(
            summary = "Get sub category hierarchy",
            description = "Retrieve all main categories with their sub-categories in a nested structure. Public endpoint - no authentication required."
    )
    public ResponseEntity<APIResponse<List<SubCategoryResponse>>> getSubCategoryHierarchy(
            @RequestParam(defaultValue = "true") boolean activeOnly) {

        List<SubCategoryResponse> response = categoryService.getSubCategoryHierarchy(activeOnly);

        return ResponseEntity.ok(APIResponse.<List<SubCategoryResponse>>builder()
                .success(true)
                .message("sub Category hierarchy retrieved successfully")
                .data(response)
                .build());
    }

    /**
     * Update category
     * Required: ADMIN user type with EDIT_PRODUCT permission
     */
    @PutMapping("/{uuid}")
    @PreAuthorize("hasAuthority('EDIT_PRODUCT')")
    @Operation(
            summary = "Update category",
            description = "Update an existing product category. Requires ADMIN user type with EDIT_PRODUCT permission."
    )
    public ResponseEntity<APIResponse<CategoryResponse>> updateCategory(
            @PathVariable String uuid,
            @Valid @RequestBody UpdateCategoryRequest request) {
        log.info("PUT /api/v1/products/categories/{} - Updating category", uuid);

        CategoryResponse response = categoryService.updateCategory(uuid, request);

        return ResponseEntity.ok(APIResponse.<CategoryResponse>builder()
                .success(true)
                .message("Category updated successfully")
                .data(response)
                .build());
    }

    /**
     * Delete category (soft delete)
     * Required: ADMIN user type with DELETE_PRODUCT permission
     */
    @DeleteMapping("/{uuid}")
    @PreAuthorize("hasAuthority('DELETE_PRODUCT')")
    @Operation(
            summary = "Delete category",
            description = "Soft delete a product category. Requires ADMIN user type with DELETE_PRODUCT permission. Cannot delete categories with sub-categories."
    )
    public ResponseEntity<APIResponse<Void>> deleteCategory(@PathVariable String uuid) {
        log.info("DELETE /api/v1/products/categories/{} - Deleting category", uuid);

        categoryService.deleteCategory(uuid);

        return ResponseEntity.ok(APIResponse.<Void>builder()
                .success(true)
                .message("Category deleted successfully")
                .build());
    }

    /**
     * Activate category
     * Required: ADMIN user type with EDIT_PRODUCT permission
     */
    @PatchMapping("/{uuid}/activate")
    @PreAuthorize("hasAuthority('EDIT_PRODUCT')")
    @Operation(
            summary = "Activate category",
            description = "Activate a previously deleted category. Requires ADMIN user type with EDIT_PRODUCT permission."
    )
    public ResponseEntity<APIResponse<CategoryResponse>> activateCategory(@PathVariable String uuid) {
        log.info("PATCH /api/v1/products/categories/{}/activate - Activating category", uuid);

        CategoryResponse response = categoryService.activateCategory(uuid);

        return ResponseEntity.ok(APIResponse.<CategoryResponse>builder()
                .success(true)
                .message("Category activated successfully")
                .data(response)
                .build());
    }

    /**
     * Upload and set category image
     * Required: ADMIN user type with EDIT_PRODUCT permission
     */
    @PostMapping(value = "/{uuid}/image", consumes = "multipart/form-data")
    @Operation(
            summary = "Upload category image",
            description = "Upload an image for a category and store the S3 object key in the database. Requires ADMIN user type with EDIT_PRODUCT permission."
    )
    public ResponseEntity<APIResponse<CategoryResponse>> uploadCategoryImage(
            @PathVariable String uuid,
            @RequestPart("file") MultipartFile file) {
        log.info("POST /api/v1/products/categories/{}/image - Uploading category image", uuid);

        CategoryResponse response = categoryService.updateCategoryImage(uuid, file);

        return ResponseEntity.ok(APIResponse.<CategoryResponse>builder()
                .success(true)
                .message("Category image uploaded successfully")
                .data(response)
                .build());
    }

    /**
     * Get all categories with pagination
     */
    @GetMapping("/all")
    @Operation(
            summary = "Get all categories (paginated)",
            description = "Retrieve all categories with pagination support. Public endpoint - no authentication required."
    )
    public ResponseEntity<APIResponse<PageResponse<CategoryResponse>>> getAllPublicCategories(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDirection) {
        log.info("GET /api/v1/products/categories - Fetching all categories (page: {}, size: {})", page, size);

        Sort sort = sortDirection.equalsIgnoreCase("DESC")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);
        PageResponse<CategoryResponse> response = categoryService.getAllCategories(pageable);

        return ResponseEntity.ok(APIResponse.<PageResponse<CategoryResponse>>builder()
                .success(true)
                .message("Categories retrieved successfully")
                .data(response)
                .build());
    }
}
