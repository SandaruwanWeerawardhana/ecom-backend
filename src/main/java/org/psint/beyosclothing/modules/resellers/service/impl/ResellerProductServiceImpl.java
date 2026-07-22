package org.psint.beyosclothing.modules.resellers.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.inventory.entity.ProductStockEntity;
import org.psint.beyosclothing.modules.inventory.repository.ProductStockRepository;
import org.psint.beyosclothing.modules.products.entity.Product;
import org.psint.beyosclothing.modules.products.entity.ProductCategory;
import org.psint.beyosclothing.modules.products.entity.ProductVariant;
import org.psint.beyosclothing.modules.products.repository.ProductCategoryRepository;
import org.psint.beyosclothing.modules.products.repository.ProductRepository;
import org.psint.beyosclothing.modules.products.repository.ProductVariantRepository;
import org.psint.beyosclothing.modules.resellers.controller.ResellerProductController;
import org.psint.beyosclothing.modules.resellers.dto.response.ResellerProductDetailResponse;
import org.psint.beyosclothing.modules.resellers.dto.response.ResellerProductFullDetailResponse;
import org.psint.beyosclothing.modules.resellers.dto.response.ResellerProductListResponse;
import org.psint.beyosclothing.modules.resellers.dto.response.ResellerVariantPricingResponse;
import org.psint.beyosclothing.modules.resellers.dto.response.ResellerVariantThumbnailResponse;
import org.psint.beyosclothing.modules.resellers.entity.Reseller;
import org.psint.beyosclothing.modules.resellers.repository.ResellerRepository;
import org.psint.beyosclothing.modules.resellers.service.ResellerProductService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of Reseller Product Service
 * Handles product browsing with reseller-specific pricing
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ResellerProductServiceImpl implements ResellerProductService {

    private final ResellerRepository resellerRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductCategoryRepository categoryRepository;
    private final ProductStockRepository stockRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.rabbitmq.exchange.product:beyos.exchange.product}")
    private String productExchange;

    @Value("${app.rabbitmq.queue.reseller-product-detail-lookup-request:reseller.product.detail.lookup.request}")
    private String resellerProductDetailLookupQueue;

    @Override
    @Transactional(value = "productTransactionManager", readOnly = true)
    public ResellerProductListResponse getProducts(Long resellerId, String search, String category, Pageable pageable) {
        log.info("Fetching products for reseller ID: {} - Search: {}, Category: {}", resellerId, search, category);

        // Verify reseller exists and get markup rules
        Reseller reseller = resellerRepository.findById(resellerId)
                .orElseThrow(() -> new IllegalArgumentException("Reseller not found with ID: " + resellerId));

        // Get all published products
        List<Product> allProducts = productRepository.findAllResellerProducts();

        // Apply search filter
        if (search != null && !search.trim().isEmpty()) {
            String searchLower = search.toLowerCase();
            allProducts = allProducts.stream()
                    .filter(p -> p.getTitle().toLowerCase().contains(searchLower) ||
                                (p.getDescription() != null && p.getDescription().toLowerCase().contains(searchLower)) ||
                                (p.getSku() != null && p.getSku().toLowerCase().contains(searchLower)))
                    .collect(Collectors.toList());
        }

        // Apply category filter
        if (category != null && !category.trim().isEmpty()) {
            ProductCategory productCategory = categoryRepository.findByUuid(category).orElse(null);
            if (productCategory != null) {
                Long categoryId = productCategory.getId();
                allProducts = allProducts.stream()
                        .filter(p -> categoryId.equals(p.getCategoryId()))
                        .collect(Collectors.toList());
            } else {
                allProducts = new ArrayList<>();
            }
        }

        // Create page
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), allProducts.size());
        List<Product> pageContent = allProducts.subList(start, end);

        // Convert to DTOs
        List<ResellerProductListResponse.ResellerProductSummary> productSummaries = pageContent.stream()
                .map(product -> convertToProductSummary(product, reseller))
                .collect(Collectors.toList());

        return ResellerProductListResponse.builder()
                .products(productSummaries)
                .currentPage(pageable.getPageNumber())
                .totalPages((int) Math.ceil((double) allProducts.size() / pageable.getPageSize()))
                .totalProducts((long) allProducts.size())
                .pageSize(pageable.getPageSize())
                .build();
    }

    @Override
    @Transactional(value = "productTransactionManager", readOnly = true)
    public ResellerProductDetailResponse getProductByUuid(Long resellerId, String productUuid) {
        log.info("Fetching product details for UUID: {} - Reseller ID: {}", productUuid, resellerId);

        // Verify reseller exists
        Reseller reseller = resellerRepository.findById(resellerId)
                .orElseThrow(() -> new IllegalArgumentException("Reseller not found with ID: " + resellerId));

        // Get product
        Product product = productRepository.findByUuid(productUuid)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with UUID: " + productUuid));

        // Get category name
        String categoryName = null;
        if (product.getCategoryId() != null) {
            categoryName = categoryRepository.findById(product.getCategoryId())
                    .map(ProductCategory::getName)
                    .orElse(null);
        }

        // Get variants
        List<ProductVariant> variants = productVariantRepository.findByProductIdAndIsActive(product.getId(), true);
        List<ResellerProductDetailResponse.ResellerVariantInfo> variantInfos = variants.stream()
                .map(variant -> convertToVariantInfo(variant, product, reseller))
                .collect(Collectors.toList());

        // Build markup rules
        ResellerProductDetailResponse.MarkupRules markupRules = ResellerProductDetailResponse.MarkupRules.builder()
                .allowPriceOverride(reseller.getAllowPriceOverride())
                .minMarkupPercentage(reseller.getMinAllowedMarkupPct())
                .maxMarkupPercentage(reseller.getMaxAllowedMarkupPct())
                .build();

        // Check availability based on inventory stock_quantity (product_stock table)
        boolean isAvailable = !variants.isEmpty() && variants.stream()
                .anyMatch(v -> getStockQuantity(product.getId(), v.getId()) > 0);

        return ResellerProductDetailResponse.builder()
                .productUuid(product.getUuid())
                .name(product.getTitle())
                .description(product.getDescription())
                .productType(product.getProductType().name())
                .categoryName(categoryName)
                .thumbnailUrl(product.getThumbnailUrl())
                .galleryImages(new ArrayList<>()) // TODO: Add gallery images if needed
                .attributes(new ArrayList<>()) // TODO: Add attributes if needed
                .variants(variantInfos)
                .markupRules(markupRules)
                .isAvailable(isAvailable)
                .build();
    }

    @Override
    @Transactional(value = "productTransactionManager", readOnly = true)
    public ResellerVariantPricingResponse getVariantPricing(Long resellerId, String productUuid, String variantUuid) {
        log.info("Fetching variant pricing - Reseller ID: {}, Product: {}, Variant: {}",
                resellerId, productUuid, variantUuid);

        // Verify reseller exists
        Reseller reseller = resellerRepository.findById(resellerId)
                .orElseThrow(() -> new IllegalArgumentException("Reseller not found with ID: " + resellerId));

        // Get product
        Product product = productRepository.findByUuid(productUuid)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with UUID: " + productUuid));

        // Get variant
        ProductVariant variant = productVariantRepository.findByUuid(variantUuid)
                .orElseThrow(() -> new IllegalArgumentException("Variant not found with UUID: " + variantUuid));

        // Verify variant belongs to product
        if (!variant.getProductId().equals(product.getId())) {
            throw new IllegalArgumentException("Variant does not belong to the specified product");
        }

        // Calculate pricing
        BigDecimal resellerPrice = calculateResellerPrice(product, variant);
        BigDecimal showcasePrice = variant.getShowcasePrice() != null ? variant.getShowcasePrice() : product.getShowcasePrice();
        BigDecimal suggestedMargin = showcasePrice.subtract(resellerPrice);

        // Calculate min/max allowed prices based on markup rules
        BigDecimal minMarkup = reseller.getMinAllowedMarkupPct() != null ? reseller.getMinAllowedMarkupPct() : BigDecimal.ZERO;
        BigDecimal maxMarkup = reseller.getMaxAllowedMarkupPct() != null ? reseller.getMaxAllowedMarkupPct() : BigDecimal.valueOf(100);

        BigDecimal minAllowedPrice = resellerPrice.add(
                resellerPrice.multiply(minMarkup).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
        );
        BigDecimal maxAllowedPrice = resellerPrice.add(
                resellerPrice.multiply(maxMarkup).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
        );

        // Get stock
        Integer stockQuantity = getStockQuantity(product.getId(), variant.getId());

        return ResellerVariantPricingResponse.builder()
                .variantUuid(variant.getUuid())
                .productUuid(product.getUuid())
                .productName(product.getTitle())
                .sku(variant.getSku())
                .attributeSummary(variant.getAttributeSummary())
                .showcasePrice(showcasePrice)
                .resellerPrice(resellerPrice)
                .suggestedMargin(suggestedMargin)
                .suggestedSellingPrice(showcasePrice)
                .minAllowedSellingPrice(minAllowedPrice)
                .maxAllowedSellingPrice(maxAllowedPrice)
                .stockAvailable(stockQuantity)
                .isAvailable(stockQuantity > 0)
                .allowPriceOverride(reseller.getAllowPriceOverride())
                .minMarkupPercentage(reseller.getMinAllowedMarkupPct())
                .maxMarkupPercentage(reseller.getMaxAllowedMarkupPct())
                .currency("LKR")
                .thumbnailUrl(variant.getThumbnailUrl())
                .build();
    }

    @Override
    @Transactional(value = "productTransactionManager", readOnly = true)
    public ResellerProductListResponse searchProducts(Long resellerId,
                                                      ResellerProductController.ProductSearchRequest searchRequest,
                                                      Pageable pageable) {
        log.info("Searching products for reseller ID: {} - Query: {}", resellerId, searchRequest.getQuery());

        // For now, delegate to getProducts with search query
        // In future, this can be enhanced with Elasticsearch for better search
        return getProducts(resellerId, searchRequest.getQuery(), searchRequest.getCategoryUuid(), pageable);
    }

    @Override
    @Transactional(value = "productTransactionManager", readOnly = true)
    public ResellerProductFullDetailResponse getProductFullDetailByUuid(Long resellerId, String productUuid) {
        log.info("Fetching full product detail via RabbitMQ for UUID: {} - Reseller ID: {}", productUuid, resellerId);

        // Verify reseller exists and get markup rules
        Reseller reseller = resellerRepository.findById(resellerId)
                .orElseThrow(() -> new IllegalArgumentException("Reseller not found with ID: " + resellerId));

        // Send RabbitMQ RPC request to Product module
        Map<String, Object> request = new HashMap<>();
        request.put("requestId", UUID.randomUUID().toString());
        request.put("productUuid", productUuid);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = rabbitTemplate.convertSendAndReceiveAsType(
                productExchange,
                resellerProductDetailLookupQueue,
                request,
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        if (response == null || !Boolean.TRUE.equals(response.get("found"))) {
            String error = response != null ? (String) response.get("error") : "No response from product service";
            log.warn("Product not found via RabbitMQ for UUID={}: {}", productUuid, error);
            throw new IllegalArgumentException("Product not found with UUID: " + productUuid);
        }

        // Map the raw response map to typed DTO
        ResellerProductFullDetailResponse detail = mapResponseToFullDetail(response);

        // Append reseller markup rules
        detail.setMarkupRules(ResellerProductFullDetailResponse.MarkupRules.builder()
                .allowPriceOverride(reseller.getAllowPriceOverride())
                .minMarkupPercentage(reseller.getMinAllowedMarkupPct())
                .maxMarkupPercentage(reseller.getMaxAllowedMarkupPct())
                .build());

        return detail;
    }

    @Override
    public ResellerVariantThumbnailResponse getVariantThumbnail(String variantUuid) {
        log.info("Fetching variant thumbnail via RabbitMQ for variant UUID: {}", variantUuid);

        Map<String, Object> request = new HashMap<>();
        request.put("requestId", UUID.randomUUID().toString());
        request.put("requestType", "VARIANT_THUMBNAIL");
        request.put("variantUuid", variantUuid);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = rabbitTemplate.convertSendAndReceiveAsType(
                productExchange,
                resellerProductDetailLookupQueue,
                request,
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        if (response == null || !Boolean.TRUE.equals(response.get("found"))) {
            String error = response != null ? (String) response.get("error") : "No response from product service";
            log.warn("Variant not found via RabbitMQ for UUID={}: {}", variantUuid, error);
            throw new IllegalArgumentException("Variant not found with UUID: " + variantUuid);
        }

        return ResellerVariantThumbnailResponse.builder()
                .thumbnailUrl((String) response.get("thumbnailUrl"))
                .build();
    }

    // ==================== Private Helper Methods ====================

    private ResellerProductListResponse.ResellerProductSummary convertToProductSummary(Product product, Reseller reseller) {
        // Get variants for this product
        List<ProductVariant> variants = productVariantRepository.findByProductIdAndIsActive(product.getId(), true);

        // Calculate pricing
        BigDecimal showcasePrice = product.getShowcasePrice();
        BigDecimal resellerPrice = calculateResellerPriceForProduct(product);
        BigDecimal suggestedMargin = showcasePrice.subtract(resellerPrice);
        BigDecimal suggestedMarginPct = resellerPrice.compareTo(BigDecimal.ZERO) > 0
                ? suggestedMargin.multiply(BigDecimal.valueOf(100)).divide(resellerPrice, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Get total stock. Variable products store stock per variant, while simple
        // products store it in a single row with variant_id = NULL. Decide by product
        // type so simple products are not affected by stray/legacy variant rows.
        Integer totalStock;
        if (Product.ProductType.VARIABLE.equals(product.getProductType()) && !variants.isEmpty()) {
            totalStock = variants.stream()
                    .mapToInt(v -> getStockQuantity(product.getId(), v.getId()))
                    .sum();
        } else {
            totalStock = getProductLevelStockQuantity(product.getId());
        }

        // Get category name
        String categoryName = null;
        if (product.getCategoryId() != null) {
            categoryName = categoryRepository.findById(product.getCategoryId())
                    .map(ProductCategory::getName)
                    .orElse(null);
        }

        // Calculate price range for variable products
        String priceRange = null;
        if (Product.ProductType.VARIABLE.equals(product.getProductType()) && !variants.isEmpty()) {
            BigDecimal minPrice = variants.stream()
                    .map(v -> calculateResellerPrice(product, v))
                    .min(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);
            BigDecimal maxPrice = variants.stream()
                    .map(v -> calculateResellerPrice(product, v))
                    .max(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);
            priceRange = String.format("%.2f - %.2f", minPrice, maxPrice);
        }

        return ResellerProductListResponse.ResellerProductSummary.builder()
                .productUuid(product.getUuid())
                .sku(product.getSku())
                .name(product.getTitle())
                .description(product.getShortDescription())
                .thumbnailUrl(product.getThumbnailUrl())
                .productType(product.getProductType().name())
                .categoryName(categoryName)
                .showcasePrice(showcasePrice)
                .resellerPrice(resellerPrice)
                .suggestedMargin(suggestedMargin)
                .suggestedMarginPercentage(suggestedMarginPct)
                .stockAvailable(totalStock)
                .isAvailable(totalStock > 0)
                .variantCount(variants.size())
                .priceRange(priceRange)
                .build();
    }

    private ResellerProductDetailResponse.ResellerVariantInfo convertToVariantInfo(ProductVariant variant, Product product, Reseller reseller) {
        BigDecimal resellerPrice = calculateResellerPrice(product, variant);
        BigDecimal showcasePrice = variant.getShowcasePrice() != null ? variant.getShowcasePrice() : product.getShowcasePrice();
        Integer stockQuantity = getStockQuantity(product.getId(), variant.getId());

        return ResellerProductDetailResponse.ResellerVariantInfo.builder()
                .variantUuid(variant.getUuid())
                .sku(variant.getSku())
                .attributeSummary(variant.getAttributeSummary())
                .showcasePrice(showcasePrice)
                .resellerPrice(resellerPrice)
                .suggestedSellingPrice(showcasePrice)
                .stockAvailable(stockQuantity)
                .isAvailable(stockQuantity > 0)
                .thumbnailUrl(variant.getThumbnailUrl())
                .build();
    }

    private BigDecimal calculateResellerPriceForProduct(Product product) {
        // Reseller price is typically the wholesale price or production cost + margin
        // If wholesale price exists, use that; otherwise use showcase price with a default discount
        if (product.getWholesalePrice() != null && product.getWholesalePrice().compareTo(BigDecimal.ZERO) > 0) {
            return product.getWholesalePrice();
        }

        // Default: 80% of showcase price (20% margin for reseller)
        return product.getShowcasePrice().multiply(BigDecimal.valueOf(0.80)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateResellerPrice(Product product, ProductVariant variant) {
        // Check if variant has wholesale price, otherwise use product-level pricing
        if (variant.getWholesalePrice() != null && variant.getWholesalePrice().compareTo(BigDecimal.ZERO) > 0) {
            return variant.getWholesalePrice();
        }

        if (product.getWholesalePrice() != null && product.getWholesalePrice().compareTo(BigDecimal.ZERO) > 0) {
            return product.getWholesalePrice();
        }

        // Default: 80% of variant/product price
        BigDecimal basePrice = variant.getShowcasePrice() != null ? variant.getShowcasePrice() : product.getShowcasePrice();
        return basePrice.multiply(BigDecimal.valueOf(0.80)).setScale(2, RoundingMode.HALF_UP);
    }

    private Integer getStockQuantity(Long productId, Long variantId) {
        return stockRepository.findByProductIdAndVariantId(productId, variantId)
                .map(ProductStockEntity::getStockQuantity)
                .orElse(0);
    }

    // Stock for simple products, stored with variant_id = NULL.
    private Integer getProductLevelStockQuantity(Long productId) {
        return stockRepository.findByProductIdAndVariantIdIsNull(productId)
                .map(ProductStockEntity::getStockQuantity)
                .orElse(0);
    }

    // ── Mapper: raw Map → ResellerProductFullDetailResponse ──────────────

    @SuppressWarnings("unchecked")
    private ResellerProductFullDetailResponse mapResponseToFullDetail(Map<String, Object> r) {
        ResellerProductFullDetailResponse dto = new ResellerProductFullDetailResponse();
        dto.setUuid((String) r.get("uuid"));
        dto.setSku((String) r.get("sku"));
        dto.setTitle((String) r.get("title"));
        dto.setSlug((String) r.get("slug"));
        dto.setShortDescription((String) r.get("shortDescription"));
        dto.setDescription((String) r.get("description"));
        dto.setThumbnailUrl((String) r.get("thumbnailUrl"));
        dto.setShowcasePrice((String) r.get("showcasePrice"));
        dto.setSalePrice((String) r.get("salePrice"));
        dto.setSaleStart(parseDateTime((String) r.get("saleStart")));
        dto.setSaleEnd(parseDateTime((String) r.get("saleEnd")));
        dto.setProductType((String) r.get("productType"));
        dto.setFeatured((Boolean) r.get("featured"));
        dto.setVisibility((String) r.get("visibility"));
        dto.setSoldIndividually((Boolean) r.get("soldIndividually"));
        dto.setPricingMode((String) r.get("pricingMode"));
        dto.setWholesalePrice((String) r.get("wholesalePrice"));
        dto.setWholesaleMinQty((Integer) r.get("wholesaleMinQty"));
        dto.setProductionCost((String) r.get("productionCost"));
        dto.setIsPublish((Boolean) r.get("isPublish"));
        dto.setIsActive((Boolean) r.get("isActive"));
        dto.setDateCreated(parseDateTime((String) r.get("dateCreated")));
        dto.setDateUpdated(parseDateTime((String) r.get("dateUpdated")));

        // Category
        Map<String, Object> catMap = (Map<String, Object>) r.get("category");
        if (catMap != null) {
            dto.setCategory(ResellerProductFullDetailResponse.CategoryInfo.builder()
                    .uuid((String) catMap.get("uuid"))
                    .name((String) catMap.get("name"))
                    .slug((String) catMap.get("slug"))
                    .isActive((Boolean) catMap.get("isActive"))
                    .parentCategoryName((String) catMap.get("parentCategoryName"))
                    .parentCategoryUuid((String) catMap.get("parentCategoryUuid"))
                    .subCategories((List<String>) catMap.get("subCategories"))
                    .dateCreated(parseDateTime((String) catMap.get("dateCreated")))
                    .dateUpdated(parseDateTime((String) catMap.get("dateUpdated")))
                    .build());
        }

        // Gallery
        List<Map<String, Object>> galleryList = (List<Map<String, Object>>) r.get("galleryImages");
        if (galleryList != null) {
            dto.setGalleryImages(galleryList.stream().map(g ->
                    ResellerProductFullDetailResponse.GalleryImageInfo.builder()
                            .uuid((String) g.get("uuid"))
                            .mediaUrl((String) g.get("mediaUrl"))
                            .sortOrder((Integer) g.get("sortOrder"))
                            .altText((String) g.get("altText"))
                            .mimeType((String) g.get("mimeType"))
                            .build()
            ).collect(Collectors.toList()));
        }

        // Meta
        Map<String, Object> metaMap = (Map<String, Object>) r.get("meta");
        if (metaMap != null) {
            dto.setMeta(ResellerProductFullDetailResponse.MetaInfo.builder()
                    .uuid((String) metaMap.get("uuid"))
                    .metaTitle((String) metaMap.get("metaTitle"))
                    .metaDescription((String) metaMap.get("metaDescription"))
                    .canonicalUrl((String) metaMap.get("canonicalUrl"))
                    .metaKeywords((String) metaMap.get("metaKeywords"))
                    .ogTitle((String) metaMap.get("ogTitle"))
                    .ogDescription((String) metaMap.get("ogDescription"))
                    .ogImage((String) metaMap.get("ogImage"))
                    .twitterCard((String) metaMap.get("twitterCard"))
                    .jsonLd((String) metaMap.get("jsonLd"))
                    .robotIndex((Boolean) metaMap.get("robotIndex"))
                    .build());
        }

        // Dimension
        Map<String, Object> dimMap = (Map<String, Object>) r.get("dimension");
        if (dimMap != null) {
            dto.setDimension(ResellerProductFullDetailResponse.DimensionInfo.builder()
                    .uuid((String) dimMap.get("uuid"))
                    .weightKg((String) dimMap.get("weightKg"))
                    .lengthCm((String) dimMap.get("lengthCm"))
                    .widthCm((String) dimMap.get("widthCm"))
                    .heightCm((String) dimMap.get("heightCm"))
                    .build());
        }

        // Tags
        List<Map<String, Object>> tagList = (List<Map<String, Object>>) r.get("tags");
        if (tagList != null) {
            dto.setTags(tagList.stream().map(t ->
                    ResellerProductFullDetailResponse.TagInfo.builder()
                            .uuid((String) t.get("uuid"))
                            .name((String) t.get("name"))
                            .slug((String) t.get("slug"))
                            .description((String) t.get("description"))
                            .build()
            ).collect(Collectors.toList()));
        }

        // Variants
        List<Map<String, Object>> variantList = (List<Map<String, Object>>) r.get("variants");
        if (variantList != null) {
            dto.setVariants(variantList.stream().map(this::mapVariantMap).collect(Collectors.toList()));
        }

        // Default variant
        Map<String, Object> defaultVariantMap = (Map<String, Object>) r.get("defaultVariant");
        if (defaultVariantMap != null) {
            dto.setDefaultVariant(mapVariantMap(defaultVariantMap));
        }

        // Attribute maps (nested generics come back as raw Maps from Jackson deserialization)
        dto.setVariantAttributesMap((Map<String, Map<String, String>>) r.get("variantAttributesMap"));
        dto.setVariantAttributesListMap((Map<String, Map<String, List<String>>>) r.get("variantAttributesListMap"));

        return dto;
    }

    @SuppressWarnings("unchecked")
    private ResellerProductFullDetailResponse.VariantInfo mapVariantMap(Map<String, Object> v) {
        List<Map<String, Object>> avList = (List<Map<String, Object>>) v.get("attributeValues");
        List<ResellerProductFullDetailResponse.AttributeValueInfo> attrValues = null;
        if (avList != null) {
            attrValues = avList.stream().map(av ->
                    ResellerProductFullDetailResponse.AttributeValueInfo.builder()
                            .uuid((String) av.get("uuid"))
                            .value((String) av.get("value"))
                            .isActive((Boolean) av.get("isActive"))
                            .build()
            ).collect(Collectors.toList());
        }

        List<Map<String, Object>> galleryList = (List<Map<String, Object>>) v.get("galleryImages");
        List<ResellerProductFullDetailResponse.GalleryImageInfo> gallery = null;
        if (galleryList != null) {
            gallery = galleryList.stream().map(g ->
                    ResellerProductFullDetailResponse.GalleryImageInfo.builder()
                            .uuid((String) g.get("uuid"))
                            .mediaUrl((String) g.get("mediaUrl"))
                            .sortOrder((Integer) g.get("sortOrder"))
                            .altText((String) g.get("altText"))
                            .mimeType((String) g.get("mimeType"))
                            .build()
            ).collect(Collectors.toList());
        }

        return ResellerProductFullDetailResponse.VariantInfo.builder()
                .uuid((String) v.get("uuid"))
                .sku((String) v.get("sku"))
                .attributeSummary((String) v.get("attributeSummary"))
                .weightKg((String) v.get("weightKg"))
                .lengthCm((String) v.get("lengthCm"))
                .widthCm((String) v.get("widthCm"))
                .heightCm((String) v.get("heightCm"))
                .thumbnailUrl((String) v.get("thumbnailUrl"))
                .showcasePrice((String) v.get("showcasePrice"))
                .salePrice((String) v.get("salePrice"))
                .saleStart(parseDateTime((String) v.get("saleStart")))
                .saleEnd(parseDateTime((String) v.get("saleEnd")))
                .resellerPrice((String) v.get("resellerPrice"))
                .wholesalePrice((String) v.get("wholesalePrice"))
                .wholesaleMinQty((Integer) v.get("wholesaleMinQty"))
                .productionCost((String) v.get("productionCost"))
                .isActive((Boolean) v.get("isActive"))
                .attributeValues(attrValues)
                .galleryImages(gallery)
                .dateCreated(parseDateTime((String) v.get("dateCreated")))
                .dateUpdated(parseDateTime((String) v.get("dateUpdated")))
                .build();
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDateTime.parse(value);
        } catch (Exception e) {
            return null;
        }
    }
}
