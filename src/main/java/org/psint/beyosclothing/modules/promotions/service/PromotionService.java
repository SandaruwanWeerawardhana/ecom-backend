package org.psint.beyosclothing.modules.promotions.service;

import org.psint.beyosclothing.common.dto.PageResponse;
import org.psint.beyosclothing.modules.promotions.dto.request.CreatePromotionRequest;
import org.psint.beyosclothing.modules.promotions.dto.request.UpdatePromotionRequest;
import org.psint.beyosclothing.modules.promotions.dto.response.PromotionResponse;
import org.springframework.data.domain.Pageable;

/**
 * Promotion Service Interface
 */
public interface PromotionService {

    /**
     * Create a new promotion
     */
    PromotionResponse createPromotion(CreatePromotionRequest request);

    /**
     * Update existing promotion
     */
    PromotionResponse updatePromotion(String uuid, UpdatePromotionRequest request);

    /**
     * Get promotion by UUID
     */
    PromotionResponse getPromotionByUuid(String uuid);

    /**
     * Get promotion by promo code
     */
    PromotionResponse getPromotionByCode(String promoCode);

    /**
     * Get all promotions with pagination
     */
    PageResponse<PromotionResponse> getAllPromotions(Pageable pageable);

    /**
     * Get all active promotions
     */
    PageResponse<PromotionResponse> getActivePromotions(Pageable pageable);

    /**
     * Delete promotion (soft delete)
     */
    void deletePromotion(String uuid);

    /**
     * Activate promotion
     */
    PromotionResponse activatePromotion(String uuid);

    /**
     * Deactivate promotion
     */
    PromotionResponse deactivatePromotion(String uuid);

    /**
     * Validate promotion code for cart
     */
    PromotionResponse validatePromotionCode(String promoCode, Long customerId);

    /**
     * Check if product already has an active promotion
     */
    boolean hasActivePromotionForProduct(Long productId);

    /**
     * Check if category already has an active promotion
     */
    boolean hasActivePromotionForCategory(Long categoryId);

    /**
     * Get active promotion for a product (if exists)
     */
    PromotionResponse getActivePromotionForProduct(Long productId);

    /**
     * Get active promotion for a category (if exists)
     */
    PromotionResponse getActivePromotionForCategory(Long categoryId);
}
