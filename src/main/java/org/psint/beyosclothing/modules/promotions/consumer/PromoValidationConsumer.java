package org.psint.beyosclothing.modules.promotions.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.cart.dto.external.PromoValidationRequest;
import org.psint.beyosclothing.modules.cart.dto.external.PromoValidationResponse;
import org.psint.beyosclothing.modules.cart.dto.external.ProductDetailsLookupResponse;
import org.psint.beyosclothing.modules.promotions.entity.Promotion;
import org.psint.beyosclothing.modules.promotions.entity.PromotionAction;
import org.psint.beyosclothing.modules.promotions.entity.PromotionCondition;
import org.psint.beyosclothing.modules.promotions.repository.PromotionActionRepository;
import org.psint.beyosclothing.modules.promotions.repository.PromotionConditionRepository;
import org.psint.beyosclothing.modules.promotions.repository.PromotionRepository;
import org.psint.beyosclothing.modules.promotions.repository.PromotionUsageRepository;
import org.psint.beyosclothing.modules.promotions.service.CrossModuleLookupService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Promo Validation Consumer
 * Handles cross-module promo code validation requests from Cart module
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PromoValidationConsumer {

    private final PromotionRepository promotionRepository;
    private final PromotionConditionRepository conditionRepository;
    private final PromotionActionRepository actionRepository;
    private final PromotionUsageRepository usageRepository;
    private final CrossModuleLookupService crossModuleLookupService; // ✅ Added for FREE_PRODUCT price lookup

    @RabbitListener(queues = "${app.rabbitmq.queue.promo-validation-request:promotion.validate.request}")
    public PromoValidationResponse handlePromoValidationRequest(PromoValidationRequest request) {
        log.debug("Received promo validation request - Code: {}, Customer ID: {}",
                request.getPromoCode(), request.getCustomerId());

        try {
            // Find promotion by code
            Promotion promotion = promotionRepository
                    .findValidPromotionByCode(request.getPromoCode(), LocalDateTime.now())
                    .orElse(null);

            if (promotion == null) {
                log.warn("Promotion not found or expired: {}", request.getPromoCode());
                return PromoValidationResponse.builder()
                        .requestId(request.getRequestId())
                        .promoCode(request.getPromoCode())
                        .isValid(false)
                        .found(false)
                        .errorMessage("Invalid or expired promo code")
                        .build();
            }

            // Check usage limits
            if (promotion.getUsageLimit() != null) {
                long totalUsage = usageRepository.countByPromotionId(promotion.getId());
                if (totalUsage >= promotion.getUsageLimit()) {
                    return PromoValidationResponse.builder()
                            .requestId(request.getRequestId())
                            .promoCode(request.getPromoCode())
                            .isValid(false)
                            .found(true)
                            .errorMessage("Promotion has reached its usage limit")
                            .build();
                }
            }

            // Check per-user usage limits
            if (request.getCustomerId() != null && promotion.getUsageLimitPerUser() != null) {
                long userUsage = usageRepository.countByPromotionIdAndCustomerId(
                        promotion.getId(), request.getCustomerId());
                if (userUsage >= promotion.getUsageLimitPerUser()) {
                    return PromoValidationResponse.builder()
                            .requestId(request.getRequestId())
                            .promoCode(request.getPromoCode())
                            .isValid(false)
                            .found(true)
                            .errorMessage("You have reached the usage limit for this promotion")
                            .build();
                }
            }

            // Validate conditions
            List<PromotionCondition> conditions = conditionRepository
                    .findAllByPromotionIdAndIsActiveTrue(promotion.getId());

            boolean conditionsMet = validateConditions(conditions, request);
            if (!conditionsMet) {
                return PromoValidationResponse.builder()
                        .requestId(request.getRequestId())
                        .promoCode(request.getPromoCode())
                        .isValid(false)
                        .found(true)
                        .errorMessage("Promotion conditions not met")
                        .build();
            }

            // Calculate discount
            BigDecimal calculatedDiscount = calculateDiscount(promotion, request);

            PromoValidationResponse response = PromoValidationResponse.builder()
                    .requestId(request.getRequestId())
                    .promotionId(promotion.getId())
                    .promoCode(promotion.getPromoCode())
                    .isValid(true)
                    .discountType(promotion.getDiscountType().name())
                    .discountValue(promotion.getDiscountValue())
                    .calculatedDiscount(calculatedDiscount)
                    .found(true)
                    .build();

            log.debug("Promo validation successful - Discount: {}", calculatedDiscount);
            return response;

        } catch (Exception e) {
            log.error("Error processing promo validation request", e);
            return PromoValidationResponse.builder()
                    .requestId(request.getRequestId())
                    .promoCode(request.getPromoCode())
                    .isValid(false)
                    .found(false)
                    .errorMessage("Error: " + e.getMessage())
                    .build();
        }
    }

    private boolean validateConditions(List<PromotionCondition> conditions, PromoValidationRequest request) {
        if (conditions.isEmpty()) {
            return true; // No conditions means promotion applies to all
        }

        for (PromotionCondition condition : conditions) {
            if (!validateSingleCondition(condition, request)) {
                log.debug("Condition not met: {} - {}", condition.getConditionType(), condition.getDescription());
                return false;
            }
        }

        return true;
    }

    private boolean validateSingleCondition(PromotionCondition condition, PromoValidationRequest request) {
        switch (condition.getConditionType()) {
            case MIN_CART_TOTAL:
                // Minimum cart total amount
                if (condition.getMinAmount() != null) {
                    if (request.getCartSubtotal().compareTo(condition.getMinAmount()) < 0) {
                        log.debug("Cart subtotal {} is less than minimum required {}",
                                request.getCartSubtotal(), condition.getMinAmount());
                        return false;
                    }
                }
                break;

            case MIN_ITEM_QUANTITY:
                // Minimum quantity of a specific item (should be MIN_CART_TOTAL actually, fixing the logic)
                if (condition.getMinAmount() != null) {
                    if (request.getCartSubtotal().compareTo(condition.getMinAmount()) < 0) {
                        return false;
                    }
                }
                break;

            case MIN_TOTAL_ITEMS:
                // Minimum total quantity of all items in cart
                if (condition.getMinQuantity() != null) {
                    int totalQuantity = request.getCartItems().stream()
                            .mapToInt(PromoValidationRequest.CartItemInfo::getQuantity)
                            .sum();
                    if (totalQuantity < condition.getMinQuantity()) {
                        log.debug("Total items {} is less than minimum required {}",
                                totalQuantity, condition.getMinQuantity());
                        return false;
                    }
                }
                break;

            case MIN_PRODUCT_QUANTITY:
                // Minimum quantity of a specific product
                if (condition.getProductId() != null && condition.getMinQuantity() != null) {
                    int productQuantity = request.getCartItems().stream()
                            .filter(item -> condition.getProductId().equals(item.getProductId()))
                            .mapToInt(PromoValidationRequest.CartItemInfo::getQuantity)
                            .sum();

                    if (productQuantity < condition.getMinQuantity()) {
                        log.debug("Product {} quantity {} is less than minimum required {}",
                                condition.getProductId(), productQuantity, condition.getMinQuantity());
                        return false;
                    }
                }
                break;

            case MIN_CATEGORY_ITEM_COUNT:
                // Minimum number of items from a specific category
                if (condition.getCategoryId() != null && condition.getMinQuantity() != null) {
                    // ✅ NOW IMPLEMENTED - Cart items include categoryId from product lookup
                    int categoryItemCount = request.getCartItems().stream()
                            .filter(item -> condition.getCategoryId().equals(item.getCategoryId()))
                            .mapToInt(PromoValidationRequest.CartItemInfo::getQuantity)
                            .sum();

                    if (categoryItemCount < condition.getMinQuantity()) {
                        log.debug("Category {} item count {} is less than minimum required {}",
                                condition.getCategoryId(), categoryItemCount, condition.getMinQuantity());
                        return false;
                    }
                }
                break;

            case BUY_X_GET_Y_TRIGGER:
                // Buy X items to trigger promotion (for Buy X Get Y free promotions)
                if (condition.getProductId() != null && condition.getMinQuantity() != null) {
                    int productQuantity = request.getCartItems().stream()
                            .filter(item -> condition.getProductId().equals(item.getProductId()))
                            .mapToInt(PromoValidationRequest.CartItemInfo::getQuantity)
                            .sum();

                    if (productQuantity < condition.getMinQuantity()) {
                        log.debug("Buy X Get Y trigger not met - need {} items, have {}",
                                condition.getMinQuantity(), productQuantity);
                        return false;
                    }
                }
                break;

            default:
                log.warn("Unknown condition type: {}", condition.getConditionType());
                return true; // Unknown conditions are ignored
        }

        return true;
    }

    private BigDecimal calculateDiscount(Promotion promotion, PromoValidationRequest request) {
        BigDecimal discount = BigDecimal.ZERO;

        // Get promotion actions to determine how discount should be calculated
        List<PromotionAction> actions = actionRepository.findAllByPromotionIdAndIsActiveTrue(promotion.getId());

        if (actions.isEmpty()) {
            // Fallback to promotion-level discount if no specific actions
            return calculatePromotionLevelDiscount(promotion, request.getCartSubtotal());
        }

        // Calculate discount based on actions
        for (PromotionAction action : actions) {
            BigDecimal actionDiscount = calculateActionDiscount(action, request);
            discount = discount.add(actionDiscount);
        }

        // Ensure discount doesn't exceed cart total
        if (discount.compareTo(request.getCartSubtotal()) > 0) {
            discount = request.getCartSubtotal();
        }

        return discount;
    }

    private BigDecimal calculatePromotionLevelDiscount(Promotion promotion, BigDecimal cartSubtotal) {
        BigDecimal discount = BigDecimal.ZERO;

        switch (promotion.getDiscountType()) {
            case PERCENTAGE:
                discount = cartSubtotal
                        .multiply(promotion.getDiscountValue())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                break;
            case FIXED:
                discount = promotion.getDiscountValue();
                // Ensure discount doesn't exceed cart total
                if (discount.compareTo(cartSubtotal) > 0) {
                    discount = cartSubtotal;
                }
                break;
            case FREE_PRODUCT:
                // Free product handled by actions
                discount = BigDecimal.ZERO;
                break;
        }

        return discount;
    }

    private BigDecimal calculateActionDiscount(PromotionAction action, PromoValidationRequest request) {
        BigDecimal discount = BigDecimal.ZERO;

        switch (action.getActionType()) {
            case APPLY_PERCENTAGE_DISCOUNT:
                // Apply percentage discount to entire cart
                if (action.getDiscountValue() != null) {
                    discount = request.getCartSubtotal()
                            .multiply(action.getDiscountValue())
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                }
                break;

            case APPLY_FIXED_DISCOUNT:
                // Apply fixed amount discount
                if (action.getDiscountValue() != null) {
                    discount = action.getDiscountValue();
                }
                break;

            case DISCOUNT_SPECIFIC_PRODUCT:
                // Apply discount to specific product(s)
                if (action.getProductId() != null && action.getDiscountValue() != null) {
                    BigDecimal productTotal = request.getCartItems().stream()
                            .filter(item -> action.getProductId().equals(item.getProductId()))
                            .map(item -> item.getPrice().multiply(new BigDecimal(item.getQuantity())))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    discount = productTotal
                            .multiply(action.getDiscountValue())
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                }
                break;

            case DISCOUNT_SPECIFIC_CATEGORY:
                // ✅ NOW IMPLEMENTED - Discount for specific category
                if (action.getCategoryId() != null && action.getDiscountValue() != null) {
                    // Calculate total value of all items in the specified category
                    BigDecimal categoryTotal = request.getCartItems().stream()
                            .filter(item -> action.getCategoryId().equals(item.getCategoryId()))
                            .map(item -> item.getPrice().multiply(new BigDecimal(item.getQuantity())))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    if (categoryTotal.compareTo(BigDecimal.ZERO) > 0) {
                        discount = categoryTotal
                                .multiply(action.getDiscountValue())
                                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

                        log.debug("Category {} discount calculated - Category total: {}, Discount %: {}, Discount amount: {}",
                                action.getCategoryId(), categoryTotal, action.getDiscountValue(), discount);
                    }
                }
                break;

            case FREE_PRODUCT:

                if (action.getFreeProductId() != null) {
                    // Fetch free product price from Product module
                    ProductDetailsLookupResponse freeProduct = crossModuleLookupService.lookupProductDetailsById(
                            action.getFreeProductId(), null);

                    if (freeProduct != null && freeProduct.getFound()) {
                        // Use sale price if available, otherwise use showcase price
                        BigDecimal freeProductPrice = freeProduct.getSalePrice() != null
                                ? freeProduct.getSalePrice()
                                : freeProduct.getShowcasePrice();

                        // Calculate discount based on free product quantity
                        Integer freeQty = action.getFreeQuantity() != null ? action.getFreeQuantity() : 1;
                        discount = freeProductPrice.multiply(new BigDecimal(freeQty));

                        log.info("FREE_PRODUCT discount calculated - Product ID: {}, Price: {}, Quantity: {}, Total Discount: {}",
                                action.getFreeProductId(), freeProductPrice, freeQty, discount);
                    } else {
                        log.warn("Free product not found - Product ID: {}. Cannot calculate discount.",
                                action.getFreeProductId());
                        // Fallback: No discount if product not found
                        discount = BigDecimal.ZERO;
                    }
                }
                break;

            default:
                log.warn("Unknown action type: {}", action.getActionType());
        }

        return discount;
    }
}
