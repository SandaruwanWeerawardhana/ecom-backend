package org.psint.beyosclothing.modules.products.service;

import org.psint.beyosclothing.common.dto.PageResponse;
import org.psint.beyosclothing.modules.products.dto.request.CreateCategoryRequest;
import org.psint.beyosclothing.modules.products.dto.request.UpdateCategoryRequest;
import org.psint.beyosclothing.modules.products.dto.response.CategoryResponse;
import org.psint.beyosclothing.modules.products.dto.response.SubCategoryResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Product Category Service Interface
 * Business logic for product category management
 */
public interface ProductCategoryService {

    /**
     * Create a new product category (main or sub-category)
     * @param request Category creation request
     * @return Created category details
     */
    CategoryResponse createCategory(CreateCategoryRequest request);

    /**
     * Get category by UUID
     * @param uuid Category UUID
     * @return Category details
     */
    CategoryResponse getCategoryByUuid(String uuid);

    /**
     * Get all main categories (without parent)
     * @param activeOnly Whether to fetch only active categories
     * @return List of main categories
     */
    List<CategoryResponse> getMainCategories(boolean activeOnly);

    /**
     * Get sub-categories of a parent category
     * @param parentUuid Parent category UUID
     * @param activeOnly Whether to fetch only active sub-categories
     * @return List of sub-categories
     */
    List<CategoryResponse> getSubCategories(String parentUuid, boolean activeOnly);

    /**
     * Get all categories with pagination
     * @param pageable Pagination parameters
     * @return Paginated category list
     */
    PageResponse<CategoryResponse> getAllCategories(Pageable pageable);

    /**
     * Get category hierarchy (main categories with their sub-categories)
     * @param activeOnly Whether to fetch only active categories
     * @return List of categories with hierarchy
     */
    List<CategoryResponse> getCategoryHierarchy(boolean activeOnly);

    /**
     * Update category
     * @param uuid Category UUID
     * @param request Update request
     * @return Updated category details
     */
    CategoryResponse updateCategory(String uuid, UpdateCategoryRequest request);

    /**
     * Delete category (soft delete)
     * @param uuid Category UUID
     */
    void deleteCategory(String uuid);

    /**
     * Activate category
     * @param uuid Category UUID
     * @return Updated category details
     */
    CategoryResponse activateCategory(String uuid);

    /**
     * Upload and set category image (stores S3 image key/url in DB)
     * @param uuid Category UUID
     * @param file Multipart image file
     * @return Updated category details with image URL
     */
    CategoryResponse updateCategoryImage(String uuid, MultipartFile file);

    /**
     * Get sub-category hierarchy: returns main categories with nested sub-category objects
     * @param activeOnly whether to include only active categories
     * @return list of SubCategoryResponse (main categories with nested subcategories)
     */
    List<SubCategoryResponse> getSubCategoryHierarchy(boolean activeOnly);
}
