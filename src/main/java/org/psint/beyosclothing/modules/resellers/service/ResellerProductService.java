package org.psint.beyosclothing.modules.resellers.service;

import org.psint.beyosclothing.modules.resellers.controller.ResellerProductController;
import org.psint.beyosclothing.modules.resellers.dto.response.ResellerProductDetailResponse;
import org.psint.beyosclothing.modules.resellers.dto.response.ResellerProductFullDetailResponse;
import org.psint.beyosclothing.modules.resellers.dto.response.ResellerProductListResponse;
import org.psint.beyosclothing.modules.resellers.dto.response.ResellerVariantPricingResponse;
import org.psint.beyosclothing.modules.resellers.dto.response.ResellerVariantThumbnailResponse;
import org.springframework.data.domain.Pageable;

/**
 * Service interface for Reseller Product operations
 */
public interface ResellerProductService {

    /**
     * Get products with reseller pricing
     * @param resellerId Reseller ID
     * @param search Search query
     * @param category Category filter
     * @param pageable Pagination
     * @return Paginated product list
     */
    ResellerProductListResponse getProducts(Long resellerId, String search, String category, Pageable pageable);

    /**
     * Get product details by UUID
     * @param resellerId Reseller ID
     * @param productUuid Product UUID
     * @return Product details with reseller pricing
     */
    ResellerProductDetailResponse getProductByUuid(Long resellerId, String productUuid);

    /**
     * Get variant pricing details
     * @param resellerId Reseller ID
     * @param productUuid Product UUID
     * @param variantUuid Variant UUID
     * @return Variant pricing information
     */
    ResellerVariantPricingResponse getVariantPricing(Long resellerId, String productUuid, String variantUuid);

    /**
     * Search products with advanced filters
     * @param resellerId Reseller ID
     * @param searchRequest Search criteria
     * @param pageable Pagination
     * @return Paginated search results
     */
    ResellerProductListResponse searchProducts(Long resellerId, ResellerProductController.ProductSearchRequest searchRequest, Pageable pageable);

    /**
     * Get full product detail via RabbitMQ cross-module lookup (no direct Product repo dependency)
     * Mirrors ProductController.getProductBySlug() response structure
     *
     * @param resellerId   Reseller ID (used to append markup rules to response)
     * @param productUuid  Product UUID
     * @return Full product detail enriched with reseller markup rules
     */
    ResellerProductFullDetailResponse getProductFullDetailByUuid(Long resellerId, String productUuid);

    /**
     * Get variant thumbnail URL via RabbitMQ cross-module lookup (no direct Product repo dependency)
     * Mirrors ProductController.getVariantGalleryImages() response structure
     *
     * @param variantUuid Variant UUID
     * @return Variant thumbnail URL response
     */
    ResellerVariantThumbnailResponse getVariantThumbnail(String variantUuid);
}