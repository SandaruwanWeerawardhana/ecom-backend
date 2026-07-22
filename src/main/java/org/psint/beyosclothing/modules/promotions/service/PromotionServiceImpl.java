package org.psint.beyosclothing.modules.promotions.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.common.dto.PageResponse;
import org.psint.beyosclothing.core.exception.ResourceNotFoundException;
import org.psint.beyosclothing.core.exception.BadRequestException;
import org.psint.beyosclothing.modules.promotions.dto.request.*;
import org.psint.beyosclothing.modules.promotions.dto.response.*;
import org.psint.beyosclothing.modules.promotions.entity.*;
import org.psint.beyosclothing.modules.promotions.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Promotion Service Implementation
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional("promotionTransactionManager")
public class PromotionServiceImpl implements PromotionService {

    private final PromotionRepository promotionRepository;
    private final PromotionConditionRepository conditionRepository;
    private final PromotionActionRepository actionRepository;
    private final PromotionUsageRepository usageRepository;
    private final PromotionProductMapRepository productMapRepository;
    private final PromotionCategoryMapRepository categoryMapRepository;

    @Override
    public PromotionResponse createPromotion(CreatePromotionRequest request) {
        log.info("Creating promotion: {}", request.getName());

        // Validate promo code uniqueness
        if (request.getPromoCode() != null && !request.getPromoCode().isEmpty()) {
            if (promotionRepository.existsByPromoCode(request.getPromoCode())) {
                throw new BadRequestException("Promo code already exists: " + request.getPromoCode());
            }
        }

        // BUSINESS RULE: For DISCOUNT_SPECIFIC_PRODUCT and DISCOUNT_SPECIFIC_CATEGORY actions ONLY
        // Check if product/category already has an active promotion with these specific action types
        if (request.getActions() != null && !request.getActions().isEmpty()) {
            for (PromotionActionRequest action : request.getActions()) {
                // Check for DISCOUNT_SPECIFIC_PRODUCT
                if ("DISCOUNT_SPECIFIC_PRODUCT".equals(action.getActionType()) && action.getProductId() != null) {
                    if (hasActivePromotionForProductWithSpecificDiscount(action.getProductId())) {
                        throw new BadRequestException(
                            "Product ID " + action.getProductId() + " already has an active DISCOUNT_SPECIFIC_PRODUCT promotion. " +
                            "Only one specific product discount can be active at a time (with or without promo code). " +
                            "Please deactivate the existing promotion before creating a new one."
                        );
                    }
                }

                // Check for DISCOUNT_SPECIFIC_CATEGORY
                if ("DISCOUNT_SPECIFIC_CATEGORY".equals(action.getActionType()) && action.getCategoryId() != null) {
                    if (hasActivePromotionForCategoryWithSpecificDiscount(action.getCategoryId())) {
                        throw new BadRequestException(
                            "Category ID " + action.getCategoryId() + " already has an active DISCOUNT_SPECIFIC_CATEGORY promotion. " +
                            "Only one specific category discount can be active at a time (with or without promo code). " +
                            "Please deactivate the existing promotion before creating a new one."
                        );
                    }
                }
            }
        }

        // Create promotion entity
        Promotion promotion = Promotion.builder()
                .name(request.getName())
                .description(request.getDescription())
                .promoCode(request.getPromoCode())
                .isCodeRequired(request.getIsCodeRequired())
                .discountType(Promotion.DiscountType.valueOf(request.getDiscountType()))
                .discountValue(request.getDiscountValue())
                .startAt(request.getStartAt())
                .endAt(request.getEndAt())
                .isStackable(request.getIsStackable())
                .usageLimit(request.getUsageLimit())
                .usageLimitPerUser(request.getUsageLimitPerUser())
                .isActive(true)
                .build();

        promotion = promotionRepository.save(promotion);
        log.info("Promotion created with ID: {}", promotion.getId());

        // Save conditions
        if (request.getConditions() != null && !request.getConditions().isEmpty()) {
            saveConditions(promotion.getId(), request.getConditions());
        }

        // Save actions
        if (request.getActions() != null && !request.getActions().isEmpty()) {
            saveActions(promotion.getId(), request.getActions());
        }

        // Save product mappings
        if (request.getProductIds() != null && !request.getProductIds().isEmpty()) {
            saveProductMappings(promotion.getId(), request.getProductIds());
        }

        // Save category mappings
        if (request.getCategoryIds() != null && !request.getCategoryIds().isEmpty()) {
            saveCategoryMappings(promotion.getId(), request.getCategoryIds());
        }

        return buildPromotionResponse(promotion);
    }

    @Override
    public PromotionResponse updatePromotion(String uuid, UpdatePromotionRequest request) {
        log.info("Updating promotion: {}", uuid);

        Promotion promotion = promotionRepository.findByUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion not found: " + uuid));

        // Update fields
        if (request.getName() != null) promotion.setName(request.getName());
        if (request.getDescription() != null) promotion.setDescription(request.getDescription());
        if (request.getPromoCode() != null) {
            if (!request.getPromoCode().equals(promotion.getPromoCode())) {
                if (promotionRepository.existsByPromoCode(request.getPromoCode())) {
                    throw new BadRequestException("Promo code already exists: " + request.getPromoCode());
                }
                promotion.setPromoCode(request.getPromoCode());
            }
        }
        if (request.getIsCodeRequired() != null) promotion.setIsCodeRequired(request.getIsCodeRequired());
        if (request.getDiscountType() != null) promotion.setDiscountType(Promotion.DiscountType.valueOf(request.getDiscountType()));
        if (request.getDiscountValue() != null) promotion.setDiscountValue(request.getDiscountValue());
        if (request.getStartAt() != null) promotion.setStartAt(request.getStartAt());
        if (request.getEndAt() != null) promotion.setEndAt(request.getEndAt());
        if (request.getIsStackable() != null) promotion.setIsStackable(request.getIsStackable());
        if (request.getUsageLimit() != null) promotion.setUsageLimit(request.getUsageLimit());
        if (request.getUsageLimitPerUser() != null) promotion.setUsageLimitPerUser(request.getUsageLimitPerUser());

        promotion = promotionRepository.save(promotion);

        // Update conditions
        if (request.getConditions() != null) {
            conditionRepository.deleteAllByPromotionId(promotion.getId());
            saveConditions(promotion.getId(), request.getConditions());
        }

        // Update actions
        if (request.getActions() != null) {
            actionRepository.deleteAllByPromotionId(promotion.getId());
            saveActions(promotion.getId(), request.getActions());
        }

        // Update product mappings
        if (request.getProductIds() != null) {
            productMapRepository.deleteAllByPromotionId(promotion.getId());
            saveProductMappings(promotion.getId(), request.getProductIds());
        }

        // Update category mappings
        if (request.getCategoryIds() != null) {
            categoryMapRepository.deleteAllByPromotionId(promotion.getId());
            saveCategoryMappings(promotion.getId(), request.getCategoryIds());
        }

        return buildPromotionResponse(promotion);
    }

    @Override
    @Transactional(value = "promotionTransactionManager", readOnly = true)
    public PromotionResponse getPromotionByUuid(String uuid) {
        log.info("Fetching promotion by UUID: {}", uuid);

        Promotion promotion = promotionRepository.findByUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion not found: " + uuid));

        return buildPromotionResponse(promotion);
    }

    @Override
    @Transactional(value = "promotionTransactionManager", readOnly = true)
    public PromotionResponse getPromotionByCode(String promoCode) {
        log.info("Fetching promotion by code: {}", promoCode);

        Promotion promotion = promotionRepository.findByPromoCode(promoCode)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion not found with code: " + promoCode));

        return buildPromotionResponse(promotion);
    }

    @Override
    @Transactional(value = "promotionTransactionManager", readOnly = true)
    public PageResponse<PromotionResponse> getAllPromotions(Pageable pageable) {
        log.info("Fetching all promotions (page: {}, size: {})", pageable.getPageNumber(), pageable.getPageSize());

        Page<Promotion> promotionPage = promotionRepository.findAll(pageable);

        List<PromotionResponse> responses = promotionPage.getContent().stream()
                .map(this::buildPromotionResponse)
                .collect(Collectors.toList());

        return PageResponse.<PromotionResponse>builder()
                .content(responses)
                .pageNumber(promotionPage.getNumber())
                .pageSize(promotionPage.getSize())
                .totalElements(promotionPage.getTotalElements())
                .totalPages(promotionPage.getTotalPages())
                .last(promotionPage.isLast())
                .build();
    }

    @Override
    @Transactional(value = "promotionTransactionManager", readOnly = true)
    public PageResponse<PromotionResponse> getActivePromotions(Pageable pageable) {
        log.info("Fetching active promotions");

        Page<Promotion> promotionPage = promotionRepository.findActivePromotions(LocalDateTime.now(), pageable);

        List<PromotionResponse> responses = promotionPage.getContent().stream()
                .map(this::buildPromotionResponse)
                .collect(Collectors.toList());

        return PageResponse.<PromotionResponse>builder()
                .content(responses)
                .pageNumber(promotionPage.getNumber())
                .pageSize(promotionPage.getSize())
                .totalElements(promotionPage.getTotalElements())
                .totalPages(promotionPage.getTotalPages())
                .last(promotionPage.isLast())
                .build();
    }

    @Override
    public void deletePromotion(String uuid) {
        log.info("Deleting promotion: {}", uuid);

        Promotion promotion = promotionRepository.findByUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion not found: " + uuid));

        promotion.setIsActive(false);
        promotionRepository.save(promotion);

        log.info("Promotion soft deleted: {}", uuid);
    }

    @Override
    public PromotionResponse activatePromotion(String uuid) {
        log.info("Activating promotion: {}", uuid);

        Promotion promotion = promotionRepository.findByUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion not found: " + uuid));

        promotion.setIsActive(true);
        promotion = promotionRepository.save(promotion);

        return buildPromotionResponse(promotion);
    }

    @Override
    public PromotionResponse deactivatePromotion(String uuid) {
        log.info("Deactivating promotion: {}", uuid);

        Promotion promotion = promotionRepository.findByUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion not found: " + uuid));

        promotion.setIsActive(false);
        promotion = promotionRepository.save(promotion);

        return buildPromotionResponse(promotion);
    }

    @Override
    @Transactional(value = "promotionTransactionManager", readOnly = true)
    public PromotionResponse validatePromotionCode(String promoCode, Long customerId) {
        log.info("Validating promotion code: {} for customer: {}", promoCode, customerId);

        Promotion promotion = promotionRepository.findValidPromotionByCode(promoCode, LocalDateTime.now())
                .orElseThrow(() -> new BadRequestException("Invalid or expired promo code: " + promoCode));

        // Check usage limits
        if (promotion.getUsageLimit() != null) {
            long totalUsage = usageRepository.countByPromotionId(promotion.getId());
            if (totalUsage >= promotion.getUsageLimit()) {
                throw new BadRequestException("Promotion has reached its usage limit");
            }
        }

        // Check per-user usage limits
        if (customerId != null && promotion.getUsageLimitPerUser() != null) {
            long userUsage = usageRepository.countByPromotionIdAndCustomerId(promotion.getId(), customerId);
            if (userUsage >= promotion.getUsageLimitPerUser()) {
                throw new BadRequestException("You have reached the usage limit for this promotion");
            }
        }

        return buildPromotionResponse(promotion);
    }

    // Helper methods
    private void saveConditions(Long promotionId, List<PromotionConditionRequest> conditions) {
        for (PromotionConditionRequest conditionReq : conditions) {
            PromotionCondition condition = PromotionCondition.builder()
                    .promotionId(promotionId)
                    .description(conditionReq.getDescription())
                    .conditionType(PromotionCondition.ConditionType.valueOf(conditionReq.getConditionType()))
                    .productId(conditionReq.getProductId())
                    .categoryId(conditionReq.getCategoryId())
                    .minQuantity(conditionReq.getMinQuantity())
                    .minAmount(conditionReq.getMinAmount())
                    .isActive(true)
                    .build();
            conditionRepository.save(condition);
        }
    }

    private void saveActions(Long promotionId, List<PromotionActionRequest> actions) {
        for (PromotionActionRequest actionReq : actions) {
            PromotionAction action = PromotionAction.builder()
                    .promotionId(promotionId)
                    .actionType(PromotionAction.ActionType.valueOf(actionReq.getActionType()))
                    .productId(actionReq.getProductId())
                    .categoryId(actionReq.getCategoryId())
                    .discountValue(actionReq.getDiscountValue())
                    .freeProductId(actionReq.getFreeProductId())
                    .freeQuantity(actionReq.getFreeQuantity())
                    .isActive(true)
                    .build();
            actionRepository.save(action);
        }
    }

    private void saveProductMappings(Long promotionId, List<Long> productIds) {
        for (Long productId : productIds) {
            PromotionProductMap mapping = PromotionProductMap.builder()
                    .promotionId(promotionId)
                    .productId(productId)
                    .isActive(true)
                    .build();
            productMapRepository.save(mapping);
        }
    }

    private void saveCategoryMappings(Long promotionId, List<Long> categoryIds) {
        for (Long categoryId : categoryIds) {
            PromotionCategoryMap mapping = PromotionCategoryMap.builder()
                    .promotionId(promotionId)
                    .categoryId(categoryId)
                    .isActive(true)
                    .build();
            categoryMapRepository.save(mapping);
        }
    }

    private PromotionResponse buildPromotionResponse(Promotion promotion) {
        // Get conditions
        List<PromotionConditionResponse> conditions = conditionRepository
                .findAllByPromotionIdAndIsActiveTrue(promotion.getId())
                .stream()
                .map(this::toConditionResponse)
                .collect(Collectors.toList());

        // Get actions
        List<PromotionActionResponse> actions = actionRepository
                .findAllByPromotionIdAndIsActiveTrue(promotion.getId())
                .stream()
                .map(this::toActionResponse)
                .collect(Collectors.toList());

        // Get product IDs
        List<Long> productIds = productMapRepository
                .findAllByPromotionIdAndIsActiveTrue(promotion.getId())
                .stream()
                .map(PromotionProductMap::getProductId)
                .collect(Collectors.toList());

        // Get category IDs
        List<Long> categoryIds = categoryMapRepository
                .findAllByPromotionIdAndIsActiveTrue(promotion.getId())
                .stream()
                .map(PromotionCategoryMap::getCategoryId)
                .collect(Collectors.toList());

        // Get current usage count
        long usageCount = usageRepository.countByPromotionId(promotion.getId());

        return PromotionResponse.builder()
                .uuid(promotion.getUuid())
                .name(promotion.getName())
                .description(promotion.getDescription())
                .promoCode(promotion.getPromoCode())
                .isCodeRequired(promotion.getIsCodeRequired())
                .discountType(promotion.getDiscountType().name())
                .discountValue(promotion.getDiscountValue())
                .startAt(promotion.getStartAt())
                .endAt(promotion.getEndAt())
                .isStackable(promotion.getIsStackable())
                .usageLimit(promotion.getUsageLimit())
                .usageLimitPerUser(promotion.getUsageLimitPerUser())
                .currentUsageCount(usageCount)
                .isActive(promotion.getIsActive())
                .dateCreated(promotion.getDateCreated())
                .dateUpdated(promotion.getDateUpdated())
                .conditions(conditions)
                .actions(actions)
                .productIds(productIds)
                .categoryIds(categoryIds)
                .build();
    }

    private PromotionConditionResponse toConditionResponse(PromotionCondition condition) {
        return PromotionConditionResponse.builder()
                .uuid(condition.getUuid())
                .description(condition.getDescription())
                .conditionType(condition.getConditionType().name())
                .productId(condition.getProductId())
                .categoryId(condition.getCategoryId())
                .minQuantity(condition.getMinQuantity())
                .minAmount(condition.getMinAmount())
                .build();
    }

    private PromotionActionResponse toActionResponse(PromotionAction action) {
        return PromotionActionResponse.builder()
                .uuid(action.getUuid())
                .actionType(action.getActionType().name())
                .productId(action.getProductId())
                .categoryId(action.getCategoryId())
                .discountValue(action.getDiscountValue())
                .freeProductId(action.getFreeProductId())
                .freeQuantity(action.getFreeQuantity())
                .build();
    }

    @Override
    @Transactional(value = "promotionTransactionManager", readOnly = true)
    public boolean hasActivePromotionForProduct(Long productId) {
        log.info("Checking if product {} has an active promotion", productId);

        // Find all active product mappings for this product
        List<PromotionProductMap> productMaps = productMapRepository
                .findAllByProductIdAndIsActiveTrue(productId);

        if (productMaps.isEmpty()) {
            return false;
        }

        // Check if any of these promotions are currently active
        for (PromotionProductMap map : productMaps) {
            Promotion promotion = promotionRepository.findById(map.getPromotionId()).orElse(null);
            if (promotion != null && promotion.getIsActive()) {
                LocalDateTime now = LocalDateTime.now();
                if (promotion.getStartAt().isBefore(now) || promotion.getStartAt().isEqual(now)) {
                    if (promotion.getEndAt() == null || promotion.getEndAt().isAfter(now)) {
                        return true; // Found an active promotion for this product
                    }
                }
            }
        }

        return false;
    }

    @Override
    @Transactional(value = "promotionTransactionManager", readOnly = true)
    public boolean hasActivePromotionForCategory(Long categoryId) {
        log.info("Checking if category {} has an active promotion", categoryId);

        // Find all active category mappings for this category
        List<PromotionCategoryMap> categoryMaps = categoryMapRepository
                .findAllByCategoryIdAndIsActiveTrue(categoryId);

        if (categoryMaps.isEmpty()) {
            return false;
        }

        // Check if any of these promotions are currently active
        for (PromotionCategoryMap map : categoryMaps) {
            Promotion promotion = promotionRepository.findById(map.getPromotionId()).orElse(null);
            if (promotion != null && promotion.getIsActive()) {
                LocalDateTime now = LocalDateTime.now();
                if (promotion.getStartAt().isBefore(now) || promotion.getStartAt().isEqual(now)) {
                    if (promotion.getEndAt() == null || promotion.getEndAt().isAfter(now)) {
                        return true; // Found an active promotion for this category
                    }
                }
            }
        }

        return false;
    }

    @Override
    @Transactional(value = "promotionTransactionManager", readOnly = true)
    public PromotionResponse getActivePromotionForProduct(Long productId) {
        log.info("Getting active promotion for product {}", productId);

        List<PromotionProductMap> productMaps = productMapRepository
                .findAllByProductIdAndIsActiveTrue(productId);

        for (PromotionProductMap map : productMaps) {
            Promotion promotion = promotionRepository.findById(map.getPromotionId()).orElse(null);
            if (promotion != null && promotion.getIsActive()) {
                LocalDateTime now = LocalDateTime.now();
                if (promotion.getStartAt().isBefore(now) || promotion.getStartAt().isEqual(now)) {
                    if (promotion.getEndAt() == null || promotion.getEndAt().isAfter(now)) {
                        return buildPromotionResponse(promotion);
                    }
                }
            }
        }

        throw new ResourceNotFoundException("No active promotion found for product: " + productId);
    }

    @Override
    @Transactional(value = "promotionTransactionManager", readOnly = true)
    public PromotionResponse getActivePromotionForCategory(Long categoryId) {
        log.info("Getting active promotion for category {}", categoryId);

        List<PromotionCategoryMap> categoryMaps = categoryMapRepository
                .findAllByCategoryIdAndIsActiveTrue(categoryId);

        for (PromotionCategoryMap map : categoryMaps) {
            Promotion promotion = promotionRepository.findById(map.getPromotionId()).orElse(null);
            if (promotion != null && promotion.getIsActive()) {
                LocalDateTime now = LocalDateTime.now();
                if (promotion.getStartAt().isBefore(now) || promotion.getStartAt().isEqual(now)) {
                    if (promotion.getEndAt() == null || promotion.getEndAt().isAfter(now)) {
                        return buildPromotionResponse(promotion);
                    }
                }
            }
        }

        throw new ResourceNotFoundException("No active promotion found for category: " + categoryId);
    }

    /**
     * Check if product already has an active promotion with DISCOUNT_SPECIFIC_PRODUCT action
     */
    private boolean hasActivePromotionForProductWithSpecificDiscount(Long productId) {
        log.info("Checking if product {} has an active DISCOUNT_SPECIFIC_PRODUCT promotion", productId);
        
        // Find all active actions of type DISCOUNT_SPECIFIC_PRODUCT targeting this product
        List<PromotionAction> actions = actionRepository.findAllByIsActiveTrue();
        
        for (PromotionAction action : actions) {
            if (PromotionAction.ActionType.DISCOUNT_SPECIFIC_PRODUCT.equals(action.getActionType()) 
                    && productId.equals(action.getProductId())) {
                
                // Check if the promotion itself is active
                Promotion promotion = promotionRepository.findById(action.getPromotionId()).orElse(null);
                if (promotion != null && promotion.getIsActive()) {
                    LocalDateTime now = LocalDateTime.now();
                    if ((promotion.getStartAt().isBefore(now) || promotion.getStartAt().isEqual(now)) &&
                        (promotion.getEndAt() == null || promotion.getEndAt().isAfter(now))) {
                        return true; // Found an active DISCOUNT_SPECIFIC_PRODUCT promotion for this product
                    }
                }
            }
        }
        
        return false;
    }

    /**
     * Check if category already has an active promotion with DISCOUNT_SPECIFIC_CATEGORY action
     */
    private boolean hasActivePromotionForCategoryWithSpecificDiscount(Long categoryId) {
        log.info("Checking if category {} has an active DISCOUNT_SPECIFIC_CATEGORY promotion", categoryId);
        
        // Find all active actions of type DISCOUNT_SPECIFIC_CATEGORY targeting this category
        List<PromotionAction> actions = actionRepository.findAllByIsActiveTrue();
        
        for (PromotionAction action : actions) {
            if (PromotionAction.ActionType.DISCOUNT_SPECIFIC_CATEGORY.equals(action.getActionType()) 
                    && categoryId.equals(action.getCategoryId())) {
                
                // Check if the promotion itself is active
                Promotion promotion = promotionRepository.findById(action.getPromotionId()).orElse(null);
                if (promotion != null && promotion.getIsActive()) {
                    LocalDateTime now = LocalDateTime.now();
                    if ((promotion.getStartAt().isBefore(now) || promotion.getStartAt().isEqual(now)) &&
                        (promotion.getEndAt() == null || promotion.getEndAt().isAfter(now))) {
                        return true; // Found an active DISCOUNT_SPECIFIC_CATEGORY promotion for this category
                    }
                }
            }
        }
        
        return false;
    }
}
