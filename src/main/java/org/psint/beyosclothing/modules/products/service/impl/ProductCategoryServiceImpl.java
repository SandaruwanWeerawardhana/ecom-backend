package org.psint.beyosclothing.modules.products.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.common.dto.PageResponse;
import org.psint.beyosclothing.modules.products.dto.request.CreateCategoryRequest;
import org.psint.beyosclothing.modules.products.dto.request.UpdateCategoryRequest;
import org.psint.beyosclothing.modules.products.dto.response.CategoryResponse;
import org.psint.beyosclothing.modules.products.dto.response.ImageUploadResponse;
import org.psint.beyosclothing.modules.products.dto.response.SubCategoryResponse;
import org.psint.beyosclothing.modules.products.entity.ProductCategory;
import org.psint.beyosclothing.modules.products.exception.DuplicateResourceException;
import org.psint.beyosclothing.modules.products.exception.InvalidRequestException;
import org.psint.beyosclothing.modules.products.exception.ResourceNotFoundException;
import org.psint.beyosclothing.modules.products.repository.ProductCategoryRepository;
import org.psint.beyosclothing.modules.products.repository.ProductRepository;
import org.psint.beyosclothing.modules.products.service.ImageStorageService;
import org.psint.beyosclothing.modules.products.service.ProductCategoryService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Product Category Service Implementation
 * Handles business logic for product categories
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductCategoryServiceImpl implements ProductCategoryService {

    private final ProductCategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ImageStorageService imageStorageService;

    @Override
    @Transactional
    @CacheEvict(value = {"categories", "mainCategories", "subCategories", "categoryHierarchy"}, allEntries = true)
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        log.info("Creating category with name: {}", request.getName());

        // Validate slug uniqueness
        if (categoryRepository.existsBySlug(request.getSlug()) &&  categoryRepository.existsActiveBySlug(request.getSlug())) {
            throw new DuplicateResourceException("Category with slug '" + request.getSlug() + "' already exists");
        }

        Long parentId = null;
        ProductCategory parentCategory = null;

        // Validate parent category if provided
        if (request.getParentCategoryUuid() != null && !request.getParentCategoryUuid().isBlank() &&  categoryRepository.existsActiveBySlug(request.getSlug())) {
            parentCategory = categoryRepository.findByUuid(request.getParentCategoryUuid())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Parent category not found with UUID: " + request.getParentCategoryUuid()));
            parentId = parentCategory.getId();
        }

        // Check for duplicate category name under the same parent
        if (categoryRepository.existsByNameAndParentId(request.getName(), parentId) &&  categoryRepository.existsActiveBySlug(request.getSlug())){
            String errorMessage = parentId == null
                    ? "Main category with name '" + request.getName() + "' already exists"
                    : "Sub-category with name '" + request.getName() + "' already exists under parent category '" + parentCategory.getName() + "'";
            throw new DuplicateResourceException(errorMessage);
        }

        // Create new category
        ProductCategory category = ProductCategory.builder()
                .name(request.getName())
                .slug(request.getSlug())
                .parentId(parentId)
                .isActive(true)
                .imageUrl(request.getImageUrl())
                .build();

        ProductCategory savedCategory = categoryRepository.save(category);
        log.info("Category created successfully with UUID: {}", savedCategory.getUuid());

        return mapToResponse(savedCategory, parentCategory);
    }

    @Override
    @Cacheable(value = "categories", key = "#uuid")
    public CategoryResponse getCategoryByUuid(String uuid) {
        log.info("Fetching category with UUID: {}", uuid);

        ProductCategory category = categoryRepository.findByUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with UUID: " + uuid));

        ProductCategory parentCategory = null;
        if (category.getParentId() != null) {
            parentCategory = categoryRepository.findById(category.getParentId()).orElse(null);
        }

        return mapToResponse(category, parentCategory);
    }

    @Override
    @Cacheable(value = "mainCategories", key = "#activeOnly")
    public List<CategoryResponse> getMainCategories(boolean activeOnly) {
        log.info("Fetching main categories (activeOnly: {})", activeOnly);

        List<ProductCategory> categories = activeOnly
                ? categoryRepository.findByParentIdIsNullAndIsActive(true)
                : categoryRepository.findByParentIdIsNull();

        return categories.stream()
                .map(category -> mapToResponse(category, null))
                .collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = "subCategories", key = "#parentUuid + '_' + #activeOnly")
    public List<CategoryResponse> getSubCategories(String parentUuid, boolean activeOnly) {
        log.info("Fetching sub-categories for parent UUID: {} (activeOnly: {})", parentUuid, activeOnly);

        ProductCategory parentCategory = categoryRepository.findByUuid(parentUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Parent category not found with UUID: " + parentUuid));

        List<ProductCategory> subCategories = activeOnly
                ? categoryRepository.findByParentIdAndIsActive(parentCategory.getId(), true)
                : categoryRepository.findByParentId(parentCategory.getId());

        return subCategories.stream()
                .map(category -> mapToResponse(category, parentCategory))
                .collect(Collectors.toList());
    }

    @Override
    public PageResponse<CategoryResponse> getAllCategories(Pageable pageable) {
        log.info("Fetching all categories with pagination");

        Page<ProductCategory> categoryPage = categoryRepository.findAll(pageable);

        List<CategoryResponse> responses = categoryPage.getContent().stream()
                .filter(category -> Boolean.TRUE.equals(category.getIsActive()))
                .map(category -> {
                    ProductCategory parent = null;
                    if (category.getParentId() != null) {
                        parent = categoryRepository.findById(category.getParentId()).orElse(null);
                    }
                    return mapToResponse(category, parent);
                })
                .collect(Collectors.toList());

        return PageResponse.<CategoryResponse>builder()
                .content(responses)
                .pageNumber(categoryPage.getNumber())
                .pageSize(categoryPage.getSize())
                .totalElements(categoryPage.getTotalElements())
                .totalPages(categoryPage.getTotalPages())
                .last(categoryPage.isLast())
                .first(categoryPage.isFirst())
                .empty(categoryPage.isEmpty())
                .build();
    }

    @Override
    @Cacheable(value = "categoryHierarchy", key = "#activeOnly")
    public List<CategoryResponse> getCategoryHierarchy(boolean activeOnly) {
        log.info("Fetching category hierarchy (activeOnly: {})", activeOnly);

        List<ProductCategory> mainCategories = activeOnly
                ? categoryRepository.findByParentIdIsNullAndIsActive(true)
                : categoryRepository.findByParentIdIsNull();

        return mainCategories.stream()
                .map(mainCategory -> mapToResponse(mainCategory, null))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    @CacheEvict(value = {"categories", "mainCategories", "subCategories", "categoryHierarchy"}, allEntries = true)
    public CategoryResponse updateCategory(String uuid, UpdateCategoryRequest request) {
        log.info("Updating category with UUID: {}", uuid);

        ProductCategory category = categoryRepository.findByUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with UUID: " + uuid));

        // Check slug uniqueness (if changed)
        if (!category.getSlug().equals(request.getSlug()) && categoryRepository.existsBySlug(request.getSlug()) &&  categoryRepository.existsActiveBySlug(request.getSlug())) {
            throw new DuplicateResourceException("Category with slug '" + request.getSlug() + "' already exists");
        }

        Long newParentId = null;
        ProductCategory parentCategory = null;

        // Validate parent category if provided
        if (request.getParentCategoryUuid() != null && !request.getParentCategoryUuid().isBlank()) {
            parentCategory = categoryRepository.findByUuid(request.getParentCategoryUuid())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Parent category not found with UUID: " + request.getParentCategoryUuid()));
            newParentId = parentCategory.getId();

            // Prevent self-referencing
            if (category.getId().equals(newParentId)) {
                throw new InvalidRequestException("Category cannot be its own parent");
            }
        }

        // Check for duplicate name under new parent (if name or parent changed)
        if (!category.getName().equals(request.getName()) ||
                (category.getParentId() == null && newParentId != null) ||
                (category.getParentId() != null && !category.getParentId().equals(newParentId))) {

            if (categoryRepository.existsByNameAndParentId(request.getName(), newParentId) && categoryRepository.existsActiveBySlug(request.getSlug())) {
                String errorMessage = newParentId == null
                        ? "Main category with name '" + request.getName() + "' already exists"
                        : "Sub-category with name '" + request.getName() + "' already exists under the selected parent";
                throw new DuplicateResourceException(errorMessage);
            }
        }

        // Update category
        category.setName(request.getName());
        category.setSlug(request.getSlug());
        category.setParentId(newParentId);
        category.setImageUrl(request.getImageUrl());


        ProductCategory updatedCategory = categoryRepository.save(category);
        log.info("Category updated successfully: {}", uuid);

        return mapToResponse(updatedCategory, parentCategory);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"categories", "mainCategories", "subCategories", "categoryHierarchy"}, allEntries = true)
    public void deleteCategory(String uuid) {
        log.info("Deleting category with UUID: {}", uuid);

        ProductCategory category = categoryRepository.findByUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with UUID: " + uuid));

        // Check if category has sub-categories
        List<ProductCategory> subCategories = categoryRepository.findByParentId(category.getId());
        if (!subCategories.isEmpty()) {
            throw new InvalidRequestException(
                    "Cannot delete category with existing sub-categories. Please delete or reassign sub-categories first.");
        }

        // Soft delete
        category.setIsActive(false);
        categoryRepository.save(category);
        log.info("Category soft deleted successfully: {}", uuid);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"categories", "mainCategories", "subCategories", "categoryHierarchy"}, allEntries = true)
    public CategoryResponse activateCategory(String uuid) {
        log.info("Activating category with UUID: {}", uuid);

        ProductCategory category = categoryRepository.findByUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with UUID: " + uuid));

        category.setIsActive(true);
        ProductCategory activatedCategory = categoryRepository.save(category);

        ProductCategory parentCategory = null;
        if (category.getParentId() != null) {
            parentCategory = categoryRepository.findById(category.getParentId()).orElse(null);
        }

        log.info("Category activated successfully: {}", uuid);
        return mapToResponse(activatedCategory, parentCategory);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"categories", "mainCategories", "subCategories", "categoryHierarchy"}, allEntries = true)
    public CategoryResponse updateCategoryImage(String uuid, MultipartFile file) {
        log.info("Updating category image for UUID: {}", uuid);

        ProductCategory category = categoryRepository.findByUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with UUID: " + uuid));

        // Upload image using ImageStorageService
        ImageUploadResponse uploadResponse = imageStorageService.uploadCategoryImage(file, uuid);

        // store the imageUrl (public URL) so API returns the desired categories/{uuid}.{ext} URL
        category.setImageUrl(uploadResponse.getImageUrl());

        ProductCategory updated = categoryRepository.save(category);
        log.info("Category image updated and saved for UUID: {}", uuid);

        ProductCategory parentCategory = null;
        if (updated.getParentId() != null) {
            parentCategory = categoryRepository.findById(updated.getParentId()).orElse(null);
        }

        return mapToResponse(updated, parentCategory);
    }

    @Override
    public List<SubCategoryResponse> getSubCategoryHierarchy(boolean activeOnly) {
        List<ProductCategory> mainCategories = activeOnly
                ? categoryRepository.findByParentIdIsNullAndIsActive(true)
                : categoryRepository.findByParentIdIsNull();

        return mainCategories.stream()
                .map(mc -> mapToSubCategoryResponseRecursive(mc))
                .collect(Collectors.toList());
    }

    // Recursive mapper to build SubCategoryResponse with nested children
    private SubCategoryResponse mapToSubCategoryResponseRecursive(ProductCategory category) {
        ProductCategory parent = null;
        if (category.getParentId() != null) {
            parent = categoryRepository.findById(category.getParentId()).orElse(null);
        }

        List<ProductCategory> children = categoryRepository.findByParentId(category.getId());

        List<SubCategoryResponse> childResponses = children.stream()
                .map(this::mapToSubCategoryResponseRecursive)
                .collect(Collectors.toList());

        return SubCategoryResponse.builder()
                .uuid(category.getUuid())
                .name(category.getName())
                .slug(category.getSlug())
                .parentCategoryUuid(parent != null ? parent.getUuid() : null)
                .parentCategoryName(parent != null ? parent.getName() : null)
                .isActive(category.getIsActive())
                .imageUrl(category.getImageUrl())
                .dateCreated(category.getDateCreated())
                .dateUpdated(category.getDateUpdated())
                .subCategories(childResponses)
                .productCount(getProductCountInCategory(category))
                .build();
    }

    private Long getProductCountInCategory(ProductCategory category){
        if (category == null) {
            return 0L;
        }

        // Count active products directly assigned to this category.
        Long directCount = 0L;
        try {
            directCount = productRepository.countByCategoryIdAndIsActiveTrue(category.getId());
        } catch (Exception ex) {
            log.debug("countByCategoryIdAndIsActiveTrue not available or failed, defaulting to 0: {}", ex.getMessage());
        }

        // Count products in direct sub-categories as well
        List<ProductCategory> subCategories = categoryRepository.findByParentId(category.getId());
        if (subCategories == null || subCategories.isEmpty()) {
            return directCount != null ? directCount : 0L;
        }

        long subCount = subCategories.stream()
                .mapToLong(sub -> {
                    try {
                        Long isActiveCount = productRepository.countByCategoryIdAndIsActiveTrue(sub.getId());
                        return isActiveCount != null ? isActiveCount : 0L;
                    } catch (Exception e) {
                        log.debug("countByCategoryIdAndIsActiveTrue failed for sub category {}: {}", sub.getId(), e.getMessage());
                        return 0L;
                    }
                })
                .sum();

        return (directCount != null ? directCount : 0L) + subCount;

    }

    // Helper method to map entity to response
    private CategoryResponse mapToResponse(ProductCategory category, ProductCategory parent) {
        List<String> childUuids = categoryRepository.findByParentId(category.getId()).stream()
                .map(ProductCategory::getUuid)
                .collect(Collectors.toList());

        return CategoryResponse.builder()
                .uuid(category.getUuid())
                .name(category.getName())
                .slug(category.getSlug())
                .parentCategoryUuid(parent != null ? parent.getUuid() : null)
                .parentCategoryName(parent != null ? parent.getName() : null)
                .isActive(category.getIsActive())
                .imageUrl(category.getImageUrl())
                .dateCreated(category.getDateCreated())
                .dateUpdated(category.getDateUpdated())
                .subCategories(childUuids)
                .productCount(getProductCountInCategory(category))
                .build();
    }
}

