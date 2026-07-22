package org.psint.beyosclothing.modules.products.service;

import org.psint.beyosclothing.common.dto.PageResponse;
import org.psint.beyosclothing.modules.products.dto.request.CreateProductRequest;
import org.psint.beyosclothing.modules.products.dto.request.UpdateProductRequest;
import org.psint.beyosclothing.modules.products.dto.response.*;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

/**
 * Product Service Interface
 */
public interface ProductService {

    /**
     * Create a new product with variants
     */
    ProductResponse createProduct(CreateProductRequest request);

    /**
     * Update existing product
     */
    ProductResponse updateProduct(String uuid, UpdateProductRequest request);

    /**
     * Get product by UUID
     */
    ProductResponse getProductByUuid(String uuid);

    /**
     * Get product by slug
     */
    ProductResponse getProductBySlug(String slug);

    /**
     * Get all products with pagination
     */
    PageResponse<ProductResponse> getAllProducts(Pageable pageable);

    /**
     * Get all published products
     */
    PageResponse<ProductResponse> getPublishedProducts(Pageable pageable);

    /**
     * Get all published products as product cards (for website homepage/listing)
     * Returns minimal product information with ratings and discount calculations
     */
    PageResponse<ProductCardResponse> getPublishedProductCards(Pageable pageable);

    /**
     * Get all products for filtering (optimized for e-commerce frontend)
     * Returns products with colors, sizes, ratings, discounts, and stock info
     * Returns all published products with pagination
     *
     * @param pageable Pagination parameters
     * @return Page of ProductFilterResponse
     */
    PageResponse<ProductFilterResponse> getAllProductsForFilter(Pageable pageable);

    /**
     * Get featured products
     */
    PageResponse<ProductResponse> getFeaturedProducts(Pageable pageable);

    /**
     * Get products by category UUID
     */
    PageResponse<ProductResponse> getProductsByCategory(String categoryUuid, Pageable pageable);

    /**
     * Delete product (soft delete)
     */
    void deleteProduct(String uuid);

    /**
     * Publish product
     */
    ProductResponse publishProduct(String uuid);

    /**
     * Unpublish product
     */
    ProductResponse unpublishProduct(String uuid);

    /**
     * Get variant gallery images filtered by color, size, and product type
     *
     * @param productUuid Product UUID
     * @return List of variant gallery image responses
     */
    ImageThumbnailUrlResponse getVariantGalleryImages(String productUuid);

    /**
     * Get product attributes as a map with attribute keys and values
     *
     * @param productUuid Product UUID
     * @return Map of variant UUID to map of attribute names to their values
     */
    Map<String, Map<String, String>> getProductAttributesMap(String productUuid);

    /**
     * Get product attributes as a map with attribute keys and list of values.
     * This version returns all attribute values as a list, allowing for multiple values per attribute.
     *
     * @param productUuid Product UUID
     * @return Map of variant UUID to map of attribute names to list of their values
     */
    Map<String, Map<String, List<String>>> getProductAttributesListMap(String productUuid);

}
