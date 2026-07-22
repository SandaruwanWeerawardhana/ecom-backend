package org.psint.beyosclothing.modules.promotions.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.promotions.entity.Promotion;
import org.psint.beyosclothing.modules.promotions.entity.PromotionCategoryMap;
import org.psint.beyosclothing.modules.promotions.entity.PromotionProductMap;
import org.psint.beyosclothing.modules.promotions.repository.PromotionCategoryMapRepository;
import org.psint.beyosclothing.modules.promotions.repository.PromotionProductMapRepository;
import org.psint.beyosclothing.modules.promotions.repository.PromotionRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Product Discount Consumer
 * Handles product discount calculation requests from Product module
 * Returns the highest applicable discount percentage for products
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductDiscountConsumer {

    private final PromotionRepository promotionRepository;
    private final PromotionProductMapRepository productMapRepository;
    private final PromotionCategoryMapRepository categoryMapRepository;

    @RabbitListener(queues = "${app.rabbitmq.queue.product-discount-request:promotion.product.discount.request}")
    public Map<String, Object> handleProductDiscountRequest(Map<String, Object> request) {
        String requestId = (String) request.get("requestId");

        try {
            // Support both single product and batch requests
            Object productIdObj = request.get("productId");
            Object categoryIdObj = request.get("categoryId");
            Object productIdsObj = request.get("productIds");

            log.debug("🔍 [DISCOUNT] Received discount request - ID: {}", requestId);

            // Batch request handling
            if (productIdsObj instanceof List) {
                return handleBatchDiscountRequest(requestId, (List<?>) productIdsObj, categoryIdObj);
            }

            // Single product request handling
            if (productIdObj != null) {
                Long productId = ((Number) productIdObj).longValue();
                Long categoryId = categoryIdObj != null ? ((Number) categoryIdObj).longValue() : null;

                Integer discountPercentage = calculateProductDiscount(productId, categoryId);

                Map<String, Object> response = new HashMap<>();
                response.put("requestId", requestId);
                response.put("success", true);
                response.put("productId", productId);
                response.put("discountPercentage", discountPercentage);

                log.info("✅ [DISCOUNT] Product {} → {}% discount", productId, discountPercentage);
                return response;
            }

            return buildErrorResponse(requestId, "Missing productId or productIds");

        } catch (Exception e) {
            log.error("❌ [DISCOUNT] Error processing discount request", e);
            return buildErrorResponse(requestId, "Error calculating discount: " + e.getMessage());
        }
    }

    /**
     * Handle batch discount request for multiple products
     */
    private Map<String, Object> handleBatchDiscountRequest(String requestId, List<?> productIdsObj, Object categoryIdObj) {
        try {
            List<Long> productIds = productIdsObj.stream()
                    .map(id -> ((Number) id).longValue())
                    .collect(Collectors.toList());

            Map<Long, Integer> discountMap = new HashMap<>();

            for (Long productId : productIds) {
                Long categoryId = categoryIdObj != null ? ((Number) categoryIdObj).longValue() : null;
                Integer discount = calculateProductDiscount(productId, categoryId);
                discountMap.put(productId, discount);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("requestId", requestId);
            response.put("success", true);
            response.put("discounts", discountMap);

            log.info("✅ [DISCOUNT] Batch request completed - {} products", productIds.size());
            return response;

        } catch (Exception e) {
            log.error("❌ [DISCOUNT] Error in batch processing", e);
            return buildErrorResponse(requestId, "Error in batch discount calculation");
        }
    }

    /**
     * Calculate the highest applicable discount percentage for a product
     * Priority: Direct product promotion > Category promotion
     */
    private Integer calculateProductDiscount(Long productId, Long categoryId) {
        LocalDateTime now = LocalDateTime.now();
        Integer highestDiscount = 0;

        // Find all active promotions
        List<Promotion> activePromotions = promotionRepository.findAll().stream()
                .filter(p -> p.getIsActive()
                        && p.getStartAt().isBefore(now)
                        && (p.getEndAt() == null || p.getEndAt().isAfter(now))
                        && !p.getIsCodeRequired() // Only auto-applied promotions
                        && p.getDiscountType() == Promotion.DiscountType.PERCENTAGE)
                .collect(Collectors.toList());

        log.debug("Found {} active auto-apply promotions", activePromotions.size());

        // Check product-specific promotions (highest priority)
        List<PromotionProductMap> productMaps = productMapRepository
                .findAllByProductIdAndIsActiveTrue(productId);

        for (PromotionProductMap productMap : productMaps) {
            Optional<Promotion> promoOpt = activePromotions.stream()
                    .filter(p -> p.getId().equals(productMap.getPromotionId()))
                    .findFirst();

            if (promoOpt.isPresent()) {
                Promotion promo = promoOpt.get();
                Integer discount = promo.getDiscountValue().intValue();
                if (discount > highestDiscount) {
                    highestDiscount = discount;
                    log.debug("Product-specific discount: {}% from promotion: {}", discount, promo.getName());
                }
            }
        }

        // Check category-based promotions if product-specific not found
        if (categoryId != null && highestDiscount == 0) {
            List<PromotionCategoryMap> categoryMaps = categoryMapRepository
                    .findAllByCategoryIdAndIsActiveTrue(categoryId);

            for (PromotionCategoryMap categoryMap : categoryMaps) {
                Optional<Promotion> promoOpt = activePromotions.stream()
                        .filter(p -> p.getId().equals(categoryMap.getPromotionId()))
                        .findFirst();

                if (promoOpt.isPresent()) {
                    Promotion promo = promoOpt.get();
                    Integer discount = promo.getDiscountValue().intValue();
                    if (discount > highestDiscount) {
                        highestDiscount = discount;
                        log.debug("Category discount: {}% from promotion: {}", discount, promo.getName());
                    }
                }
            }
        }

        return highestDiscount;
    }

    private Map<String, Object> buildErrorResponse(String requestId, String errorMessage) {
        Map<String, Object> response = new HashMap<>();
        response.put("requestId", requestId);
        response.put("success", false);
        response.put("errorMessage", errorMessage);
        response.put("discountPercentage", 0);
        return response;
    }
}

