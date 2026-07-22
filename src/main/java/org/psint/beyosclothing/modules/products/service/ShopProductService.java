package org.psint.beyosclothing.modules.products.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.common.dto.PageResponse;
import org.psint.beyosclothing.modules.products.dto.request.ShopFilterRequest;
import org.psint.beyosclothing.modules.products.dto.response.ShopFilterMetadata;
import org.psint.beyosclothing.modules.products.dto.response.ShopPageResponse;
import org.psint.beyosclothing.modules.products.dto.response.ShopProductResponse;
import org.psint.beyosclothing.modules.products.entity.*;
import org.psint.beyosclothing.modules.products.repository.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Shop Product Service
 * Handles shop page product listing with advanced filtering
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ShopProductService {

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductAttributeValueRepository attributeValueRepository;
    private final ProductAttributeRepository attributeRepository;
    private final VariantAttributeValueRepository variantAttributeValueRepository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchange.promotion:beyos.exchange.promotion}")
    private String promotionExchange;

    @Value("${app.rabbitmq.exchange.inventory:beyos.exchange.inventory}")
    private String inventoryExchange;

    /**
     * Get filtered products for shop page with pagination
     */
    public PageResponse<ShopProductResponse> getShopProducts(ShopFilterRequest filterRequest) {
        log.info("🛍️ [SHOP] Processing shop filter request - Category: {}, Price: {}-{}, Attributes: {}, InStockOnly: {}",
                filterRequest.getCategoryUuid(),
                filterRequest.getMinPrice(),
                filterRequest.getMaxPrice(),
                filterRequest.getAttributeValueUuids() != null ? filterRequest.getAttributeValueUuids().size() : 0,
                filterRequest.getInStockOnly());

        // Step 1: Get all published products
        List<Product> allProducts = productRepository.findAllPublishedProducts();
        log.info("Initial product count: {}", allProducts.size());

        // Step 2: Apply category filter
        if (filterRequest.getCategoryUuid() != null && !filterRequest.getCategoryUuid().trim().isEmpty()) {
            allProducts = filterByCategory(allProducts, filterRequest.getCategoryUuid());
        }

        // Step 3: Apply attribute filter
        if (filterRequest.getAttributeValueUuids() != null && !filterRequest.getAttributeValueUuids().isEmpty()) {
            allProducts = filterByAttributes(allProducts, filterRequest.getAttributeValueUuids());
        }

        // Step 4: Apply price filter
        if (filterRequest.getMinPrice() != null || filterRequest.getMaxPrice() != null) {
            allProducts = filterByPrice(allProducts, filterRequest.getMinPrice(), filterRequest.getMaxPrice());
        }

        // Step 5: Apply stock filter
        if (Boolean.TRUE.equals(filterRequest.getInStockOnly())) {
            allProducts = filterByStock(allProducts);
        }

        // Step 6: Apply sorting
        allProducts = applySorting(allProducts, filterRequest.getSortBy(), filterRequest.getSortDirection());
        log.info("After filtering and sorting, product count: {}", allProducts.size());

        // Step 7: Get product IDs for batch discount fetching
        List<Long> productIds = allProducts.stream().map(Product::getId).collect(Collectors.toList());
        log.info("Fetching discounts for {} products", productIds.size());
        Map<Long, Integer> discountMap = fetchBatchDiscounts(productIds);
        log.info("Received discounts for {} products", discountMap.size());
        // Step 8: Apply pagination
        int page = filterRequest.getPage() != null ? filterRequest.getPage() : 0;
        int size = filterRequest.getSize() != null ? filterRequest.getSize() : 20;

        int start = page * size;
        int end = Math.min(start + size, allProducts.size());

        if (start >= allProducts.size()) {
            log.info("Start index {} out of bounds for total products {}", start, allProducts.size());
            return PageResponse.<ShopProductResponse>builder()
                    .content(Collections.emptyList())
                    .pageNumber(page)
                    .pageSize(size)
                    .totalElements(0L)
                    .totalPages(0)
                    .build();
        }

        List<Product> pageProducts = allProducts.subList(start, end);

        // Step 9: Convert to response DTOs
        List<ShopProductResponse> shopProducts = pageProducts.stream()
                .map(product -> convertToShopProductResponse(product, discountMap.get(product.getId())))
                .collect(Collectors.toList());

        int totalPages = (int) Math.ceil((double) allProducts.size() / size);

        log.info("✅ [SHOP] Returned {} products (Page {}/{})", shopProducts.size(), page + 1, totalPages);

        return PageResponse.<ShopProductResponse>builder()
                .content(shopProducts)
                .pageNumber(page)
                .pageSize(size)
                .totalElements((long) allProducts.size())
                .totalPages(totalPages)
                .build();
    }

    /**
     * Filter products by category (main or sub-category)
     */
    private List<Product> filterByCategory(List<Product> products, String categoryUuid) {
        ProductCategory category = categoryRepository.findByUuid(categoryUuid).orElse(null);

        if (category == null) {
            log.warn("Category not found: {}", categoryUuid);
            return products;
        }

        // Get all category IDs to filter (main + sub-categories)
        Set<Long> categoryIds = new HashSet<>();
        categoryIds.add(category.getId());

        // If this is a main category, include all sub-categories
        if (category.getParentId() == null) {
            List<ProductCategory> subCategories = categoryRepository.findByParentIdAndIsActive(category.getId(), true);
            categoryIds.addAll(subCategories.stream().map(ProductCategory::getId).collect(Collectors.toSet()));
        }

        List<Product> filtered = products.stream()
                .filter(p -> p.getCategoryId() != null && categoryIds.contains(p.getCategoryId()))
                .collect(Collectors.toList());

        log.debug("Category filter: {} products match category '{}'", filtered.size(), category.getName());
        return filtered;
    }

    /**
     * Filter products by multiple attribute values
     * Products must have variants matching ALL selected attribute values
     */
    private List<Product> filterByAttributes(List<Product> products, List<String> attributeValueUuids) {
        if (attributeValueUuids == null || attributeValueUuids.isEmpty()) {
            return products;
        }

        // Convert UUIDs to IDs
        List<Long> attributeValueIds = new ArrayList<>();
        for (String uuid : attributeValueUuids) {
            attributeValueRepository.findByUuid(uuid).ifPresent(attrValue -> attributeValueIds.add(attrValue.getId()));
        }

        if (attributeValueIds.isEmpty()) {
            log.warn("No valid attribute value IDs found");
            return products;
        }

        List<Product> filtered = products.stream()
                .filter(product -> productMatchesAttributes(product, attributeValueIds))
                .collect(Collectors.toList());

        log.debug("Attribute filter: {} products match {} attributes", filtered.size(), attributeValueIds.size());
        return filtered;
    }

    /**
     * Check if product has variants matching the attribute values
     */
    private boolean productMatchesAttributes(Product product, List<Long> attributeValueIds) {
        List<ProductVariant> variants = variantRepository.findByProductIdAndIsActive(product.getId(), true);

        for (ProductVariant variant : variants) {
            List<VariantAttributeValue> variantAttrs = variantAttributeValueRepository.findByVariantId(variant.getId());
            Set<Long> variantAttrValueIds = variantAttrs.stream()
                    .map(VariantAttributeValue::getAttributeValueId)
                    .collect(Collectors.toSet());

            // Check if variant has all required attribute values
            if (variantAttrValueIds.containsAll(attributeValueIds)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Filter products by price range
     * Uses sale price if available, otherwise showcase price
     */
    private List<Product> filterByPrice(List<Product> products, BigDecimal minPrice, BigDecimal maxPrice) {
        List<Product> filtered = products.stream()
                .filter(product -> {
                    BigDecimal productPrice = getEffectivePrice(product);

                    if (minPrice != null && productPrice.compareTo(minPrice) < 0) {
                        return false;
                    }

                    return maxPrice == null || productPrice.compareTo(maxPrice) <= 0;
                })
                .collect(Collectors.toList());

        log.debug("Price filter: {} products in range {} - {}", filtered.size(), minPrice, maxPrice);
        return filtered;
    }

    /**
     * Get effective price for a product (considers variants)
     */
    private BigDecimal getEffectivePrice(Product product) {
        if (Product.ProductType.SIMPLE.equals(product.getProductType())) {
            return product.getSalePrice() != null ? product.getSalePrice() : product.getShowcasePrice();
        } else {
            // For variable products, use default variant or first variant
            ProductVariant variant = null;

            if (product.getDefaultVariantId() != null) {
                variant = variantRepository.findById(product.getDefaultVariantId()).orElse(null);
            }

            if (variant == null) {
                List<ProductVariant> variants = variantRepository.findByProductIdAndIsActive(product.getId(), true);
                if (!variants.isEmpty()) {
                    variant = variants.getFirst();
                }
            }

            if (variant != null) {
                log.info("Price here - Product ID: {}, Variant ID: {}, Sale Price: {}, Showcase Price: {}",
                        product.getId(), variant.getId(), variant.getSalePrice(), variant.getShowcasePrice());

                BigDecimal salePrice = variant.getSalePrice();
                // Use sale price if it exists and is greater than zero
                if (salePrice != null && salePrice.compareTo(BigDecimal.ZERO) > 0) {
                    return salePrice;
                }
                return variant.getShowcasePrice();
            }

            return product.getShowcasePrice();
        }
    }

    private BigDecimal getSalePrice(Product product) {
        if (Product.ProductType.SIMPLE.equals(product.getProductType())) {
            return product.getSalePrice() != null ? product.getSalePrice() : product.getShowcasePrice();
        } else {
            // For variable products, use default variant or first variant
            ProductVariant variant = null;

            if (product.getDefaultVariantId() != null) {
                variant = variantRepository.findById(product.getDefaultVariantId()).orElse(null);
            }

            if (variant == null) {
                List<ProductVariant> variants = variantRepository.findByProductIdAndIsActive(product.getId(), true);
                if (!variants.isEmpty()) {
                    variant = variants.getFirst();
                }
            }

            if (variant != null) {
                log.info("Price here - Product ID: {}, Variant ID: {}, Sale Price: {}, Showcase Price: {}",
                        product.getId(), variant.getId(), variant.getSalePrice(), variant.getShowcasePrice());

                BigDecimal salePrice = variant.getSalePrice();
                // Use sale price if it exists and is greater than zero
//                if (salePrice != null && salePrice.compareTo(BigDecimal.ZERO) > 0) {
//                    return salePrice;
//                }
//                return variant.getShowcasePrice();
                return salePrice;
            }

            return product.getShowcasePrice();
        }
    }


    private BigDecimal getShowcasePrice(Product product){
        if (Product.ProductType.SIMPLE.equals(product.getProductType())) {
            return product.getShowcasePrice();
        } else {
            // For variable products, use default variant or first variant
            ProductVariant variant = null;

            if (product.getDefaultVariantId() != null) {
                variant = variantRepository.findById(product.getDefaultVariantId()).orElse(null);
            }

            if (variant == null) {
                List<ProductVariant> variants = variantRepository.findByProductIdAndIsActive(product.getId(), true);
                if (!variants.isEmpty()) {
                    variant = variants.getFirst();
                }
            }

            if (variant != null) {
                log.info("Showcase Price here - Product ID: {}, Variant ID: {}, Showcase Price: {}",
                        product.getId(), variant.getId(), variant.getShowcasePrice());
                return variant.getShowcasePrice();
            }
            return product.getShowcasePrice();
        }

    }

    /**
     * Filter products by stock availability
     */
    private List<Product> filterByStock(List<Product> products) {
        List<Product> filtered = products.stream()
                .filter(product -> Product.InventoryStatus.IN_STOCK.equals(product.getInventoryStatus()))
                .collect(Collectors.toList());

        log.debug("Stock filter: {} products in stock", filtered.size());
        return filtered;
    }

    /**
     * Check if product has stock via RabbitMQ
     */
    private boolean hasStock(Product product) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("productId", product.getId());
            request.put("variantId", null);
            request.put("requestedQuantity", 1);

            rabbitTemplate.setReplyTimeout(TimeUnit.SECONDS.toMillis(2));

            Object response = rabbitTemplate.convertSendAndReceive(
                    inventoryExchange,
                    "inventory.stock.check",
                    request
            );
            log.info("Response from inventory service for product {}: {}", product.getId(), response);
            if (response instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> responseMap = (Map<String, Object>) response;
                Boolean found = (Boolean) responseMap.get("found");
                if (Boolean.TRUE.equals(found)) {
                    Object availableStockObj = responseMap.get("availableStock");
                    if (availableStockObj instanceof Number) {
                        return ((Number) availableStockObj).intValue() > 0;
                    }
                }
            }

            return false;
        } catch (Exception e) {
            log.debug("Error checking stock for product {}: {}", product.getId(), e.getMessage());
            return true; // Assume in stock if check fails
        }
    }

    /**
     * Apply sorting to product list
     */
    private List<Product> applySorting(List<Product> products, String sortBy, String sortDirection) {
        if (sortBy == null || sortBy.isEmpty()) {
            sortBy = "dateCreated";
        }

        boolean ascending = "ASC".equalsIgnoreCase(sortDirection);

        Comparator<Product> comparator = switch (sortBy.toLowerCase()) {
            case "price" -> Comparator.comparing(this::getEffectivePrice);
            case "title" -> Comparator.comparing(Product::getTitle);
            default -> Comparator.comparing(Product::getDateCreated);
        };

        if (!ascending) {
            comparator = comparator.reversed();
        }

        return products.stream().sorted(comparator).collect(Collectors.toList());
    }

    /**
     * Fetch discounts for multiple products in batch via RabbitMQ
     */
    private Map<Long, Integer> fetchBatchDiscounts(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            Map<String, Object> request = new HashMap<>();
            request.put("requestId", UUID.randomUUID().toString());
            request.put("productIds", productIds);

            rabbitTemplate.setReplyTimeout(TimeUnit.SECONDS.toMillis(3));

            Object response = rabbitTemplate.convertSendAndReceive(
                    promotionExchange,
                    "promotion.product.discount.request",
                    request
            );

            if (response instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> responseMap = (Map<String, Object>) response;
                Boolean success = (Boolean) responseMap.get("success");

                if (Boolean.TRUE.equals(success)) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> discountsObj = (Map<String, Object>) responseMap.get("discounts");

                    if (discountsObj != null) {
                        Map<Long, Integer> discountMap = new HashMap<>();
                        discountsObj.forEach((key, value) -> {
                            Long productId = Long.parseLong(key);
                            Integer discount = ((Number) value).intValue();
                            discountMap.put(productId, discount);
                        });

                        log.debug("Fetched discounts for {} products", discountMap.size());
                        return discountMap;
                    }
                }
            }

            return Collections.emptyMap();
        } catch (Exception e) {
            log.error("Error fetching batch discounts", e);
            return Collections.emptyMap();
        }
    }

    /**
     * Convert Product entity to ShopProductResponse
     */
    private ShopProductResponse convertToShopProductResponse(Product product, Integer discountPercentage) {
        BigDecimal salePrice = getSalePrice(product);
        log.info("Sale price for product {}: {}", product.getId(), salePrice);

        BigDecimal showcasePrice = getShowcasePrice(product);
        log.info("Showcase price for product {}: {}", product.getId(), showcasePrice);
        // Generate dummy ratings (will be replaced with real data later)
        Random random = new Random(product.getId()); // Seed with product ID for consistency
        double rating = 4.0 + random.nextDouble(); // 4.0 - 5.0
        rating = Math.round(rating * 10.0) / 10.0; // Round to 1 decimal

        Long reviewCount = 100L + random.nextInt(2000); // 100 - 2100 reviews

        return ShopProductResponse.builder()
                .uuid(product.getUuid())
                .slug(product.getSlug())
                .title(product.getTitle())
                .thumbnailUrl(product.getThumbnailUrl())
                .showcasePrice(showcasePrice)
                .salePrice(salePrice)
                .discountPercentage(discountPercentage != null ? discountPercentage : 0)
                .rating(rating)
                .reviewCount(reviewCount)
                .build();
    }


    /**
     * Get filtered products for shop page with pagination and filter metadata
     */
    public ShopPageResponse getShopProductsWithMetadata(ShopFilterRequest filterRequest) {
        log.info("🛍️ [SHOP] Processing shop filter request with metadata");

        // Get paginated products
        PageResponse<ShopProductResponse> products = getShopProducts(filterRequest);

        // Build filter metadata
        ShopFilterMetadata filterMetadata = buildFilterMetadata();

        return ShopPageResponse.builder()
                .products(products)
                .filterMetadata(filterMetadata)
                .build();
    }

    /**
     * Build filter metadata for shop page sidebar
     * Includes categories, attributes, and price range
     */
    private ShopFilterMetadata buildFilterMetadata() {
        log.info("Building filter metadata");

        // Step 1: Build categories with subcategories
        List<ShopFilterMetadata.CategoryInfo> categories = buildCategoryMetadata();

        // Step 2: Build attributes with values
        List<ShopFilterMetadata.AttributeInfo> attributes = buildAttributeMetadata();

        // Step 3: Calculate price range from all published products
        ShopFilterMetadata.PriceRangeInfo priceRange = buildPriceRangeMetadata();

        return ShopFilterMetadata.builder()
                .categories(categories)
                .attributes(attributes)
                .priceRange(priceRange)
                .build();
    }

    /**
     * Build category metadata with subcategories
     */
    private List<ShopFilterMetadata.CategoryInfo> buildCategoryMetadata() {
        // Get all main categories (parent_id is null)
        List<ProductCategory> mainCategories = categoryRepository.findByParentIdIsNullAndIsActive(true);

        return mainCategories.stream()
                .map(mainCategory -> {
                    // Get subcategories for this main category
                    List<ProductCategory> subCategories = categoryRepository.findByParentIdAndIsActive(
                            mainCategory.getId(), true);

                    List<ShopFilterMetadata.SubCategoryInfo> subCategoryInfos = subCategories.stream()
                            .map(subCategory -> ShopFilterMetadata.SubCategoryInfo.builder()
                                    .uuid(subCategory.getUuid())
                                    .name(subCategory.getName())
                                    .slug(subCategory.getSlug())
                                    .build())
                            .collect(Collectors.toList());

                    return ShopFilterMetadata.CategoryInfo.builder()
                            .uuid(mainCategory.getUuid())
                            .name(mainCategory.getName())
                            .slug(mainCategory.getSlug())
                            .subCategories(subCategoryInfos)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Build attribute metadata with values
     */
    private List<ShopFilterMetadata.AttributeInfo> buildAttributeMetadata() {
        // Get all active attributes
        List<ProductAttribute> attributes = attributeRepository.findAll().stream()
                .filter(attr -> Boolean.TRUE.equals(attr.getIsActive()))
                .collect(Collectors.toList());

        return attributes.stream()
                .map(attribute -> {
                    // Get all values for this attribute
                    List<ProductAttributeValue> attributeValues = attributeValueRepository
                            .findByAttributeId(attribute.getId()).stream()
                            .filter(val -> Boolean.TRUE.equals(val.getIsActive()))
                            .collect(Collectors.toList());

                    List<ShopFilterMetadata.AttributeValueInfo> valueInfos = attributeValues.stream()
                            .map(value -> ShopFilterMetadata.AttributeValueInfo.builder()
                                    .uuid(value.getUuid())
                                    .value(value.getValue())
                                    .build())
                            .collect(Collectors.toList());

                    return ShopFilterMetadata.AttributeInfo.builder()
                            .uuid(attribute.getUuid())
                            .name(attribute.getName())
                            .values(valueInfos)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Build price range metadata from all published products
     */
    private ShopFilterMetadata.PriceRangeInfo buildPriceRangeMetadata() {
        List<Product> allProducts = productRepository.findAllPublishedProducts();

        if (allProducts.isEmpty()) {
            return ShopFilterMetadata.PriceRangeInfo.builder()
                    .minPrice(BigDecimal.ZERO)
                    .maxPrice(BigDecimal.ZERO)
                    .build();
        }

        // Calculate min and max prices
        BigDecimal minPrice = allProducts.stream()
                .map(this::getEffectivePrice)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        BigDecimal maxPrice = allProducts.stream()
                .map(this::getEffectivePrice)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        log.info("Price range: {} - {}", minPrice, maxPrice);

        return ShopFilterMetadata.PriceRangeInfo.builder()
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .build();
    }
}