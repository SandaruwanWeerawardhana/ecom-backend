package org.psint.beyosclothing.modules.products.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.common.dto.PageResponse;
import org.psint.beyosclothing.core.exception.BadRequestException;
import org.psint.beyosclothing.core.exception.ResourceNotFoundException;
import org.psint.beyosclothing.modules.inventory.consumer.ProductEventConsumer.ProductDeletedEvent;
import org.psint.beyosclothing.modules.pos.service.PosProductSyncService;
import org.psint.beyosclothing.modules.products.dto.request.*;
import org.psint.beyosclothing.modules.products.dto.response.*;
import org.psint.beyosclothing.modules.products.entity.*;
import org.psint.beyosclothing.modules.products.repository.*;
import org.psint.beyosclothing.modules.products.service.ImageStorageService;
import org.psint.beyosclothing.modules.products.service.InventoryStockService;
import org.psint.beyosclothing.modules.products.service.ProductService;
import org.psint.beyosclothing.modules.products.service.ProductEventPublisherService;
import org.psint.beyosclothing.modules.products.events.ProductCreatedEvent;
import org.psint.beyosclothing.modules.products.events.ProductUpdatedEvent;
import org.psint.beyosclothing.modules.products.events.ProductPriceChangedEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.Cacheable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductGalleryRepository galleryRepository;
    private final ProductMetaRepository metaRepository;
    private final ProductDimensionRepository dimensionRepository;
    private final ProductTagRepository tagRepository;
    private final ProductTagMapRepository tagMapRepository;
    private final ProductPriceHistoryRepository priceHistoryRepository;
    private final ProductAttributeRepository attributeRepository;
    private final ProductAttributeValueRepository attributeValueRepository;
    private final VariantAttributeValueRepository variantAttributeValueRepository;
    private final VariantGalleryRepository variantGalleryRepository;
    private final ProductRatingAggregateRepository ratingAggregateRepository;
    private final ProductEventPublisherService eventPublisher;
    private final ImageStorageService imageStorageService;
    private final ProductLinkRepository productLinkRepository;
    private final ProductPaymentMethodMappingRepository paymentMethodMappingRepository;
    private final RabbitTemplate rabbitTemplate;
    private final InventoryStockService inventoryStockService;
    private final PosProductSyncService posProductSyncService;

    @Value("${app.rabbitmq.exchange.payment:beyos.exchange.payment}")
    private String paymentExchange;

    @Value("${app.rabbitmq.queue.product-payment-methods-lookup-request:product.payment.methods.lookup.request}")
    private String productPaymentMethodsLookupRoutingKey;

    @Value("${app.rabbitmq.queue.product-payment-methods-names-lookup-request:product.payment.methods.names.lookup.request}")
    private String productPaymentMethodsNamesLookupRoutingKey;

    @Override
    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        log.info("Creating product with SKU: {}", request.getSku());

        // ========================================
        // PHASE 1: VALIDATIONS (Before any DB operations)
        // ========================================

        // Validate slug
        validateSlug(request.getSlug(), request.getTitle());

        // Check if SKU already exists (only active products)
        if (productRepository.existsActiveBySku(request.getSku())) {
            throw new BadRequestException("Product with SKU '" + request.getSku() + "' already exists");
        }

        // Check if slug already exists (only active products)
        if (productRepository.existsActiveBySlug(request.getSlug())) {
            throw new BadRequestException("Product with slug '" + request.getSlug() + "' already exists");
        }

        // Get category by UUID
        ProductCategory category = categoryRepository.findByUuid(request.getCategoryUuid())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with UUID: " + request.getCategoryUuid()));

        // Validate product type
        Product.ProductType productType;
        try {
            productType = Product.ProductType.valueOf(request.getProductType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid product type. Must be SIMPLE or VARIABLE");
        }

        // Validate variants based on product type
        if (productType == Product.ProductType.VARIABLE) {
            // VARIABLE products require explicit variants
            if (request.getVariants() == null || request.getVariants().isEmpty()) {
                throw new BadRequestException("VARIABLE products require at least one variant");
            }
        } else if (productType == Product.ProductType.SIMPLE) {
            // SIMPLE products can have variants optional
            // If no variants provided, we'll auto-generate one from product details
            if (request.getVariants() != null && request.getVariants().size() > 1) {
                throw new BadRequestException("SIMPLE products can only have one variant");
            }
        }

        // Process attribute selections (create new attributes/values if needed)
        // This will ensure all attributes and values exist before variant creation
        if (request.getAttributeSelections() != null && !request.getAttributeSelections().isEmpty()) {
            processAttributeSelections(request.getAttributeSelections(), request.getVariants());
        }

        // ========================================
        // PHASE 1b: RESOLVE PAYMENT METHODS VIA RABBITMQ (Before DB save)
        // ========================================
        List<Map<String, Object>> resolvedPaymentMethods = new ArrayList<>();
        if (request.getPaymentMethodUuids() != null && !request.getPaymentMethodUuids().isEmpty()) {
            resolvedPaymentMethods = fetchPaymentMethodDetails(request.getPaymentMethodUuids());
            if (resolvedPaymentMethods.isEmpty()) {
                throw new BadRequestException("None of the provided payment method UUIDs could be resolved. Please check the UUIDs.");
            }
            log.info("Resolved {}/{} payment methods for product creation",
                    resolvedPaymentMethods.size(), request.getPaymentMethodUuids().size());
        }

        // ========================================
        // PHASE 2: CREATE PRODUCT ENTITY
        // ========================================

        // Create product entity
        Product product = Product.builder()
                .sku(request.getSku())
                .title(request.getTitle())
                .slug(request.getSlug())
                .shortDescription(request.getShortDescription())
                .description(request.getDescription())
                .categoryId(category.getId())
                .thumbnailUrl(toFullUrl(request.getThumbnailImageName()))
                .showcasePrice(request.getShowcasePrice())
                .salePrice(request.getSalePrice())
                .saleStart(request.getSaleStart())
                .saleEnd(request.getSaleEnd())
                .productType(productType)
                .featured(request.getFeatured())
                .visibility(Product.ProductVisibility.valueOf(request.getVisibility().toUpperCase()))
                .soldIndividually(request.getSoldIndividually())
                .pricingMode(Product.PricingMode.valueOf(request.getPricingMode().toUpperCase()))
                .wholesalePrice(request.getWholesalePrice())
                .wholesaleMinQty(request.getWholesaleMinQty())
                .productionCost(request.getProductionCost())
                .initialStockQuantity(request.getInitialStockQuantity() != null ? request.getInitialStockQuantity() : 0)
                .inventoryStatus(Product.InventoryStatus.valueOf(
                        request.getInventoryStatus() != null ? request.getInventoryStatus().toUpperCase() : "IN_STOCK"))
                .isPublish(request.getIsPublish())
                .isResellerProduct(request.getIsResellerProduct())
                .isActive(true)
                .build();

        product = productRepository.save(product);
        log.info("Product created with ID: {} and UUID: {}", product.getId(), product.getUuid());

        // ========================================
        // PHASE 3: SAVE RELATED ENTITIES (All must succeed or rollback)
        // ========================================

        try {
            // Move product thumbnail from temp folder to final product folder
            if (request.getThumbnailImageName() != null && !request.getThumbnailImageName().isEmpty()) {
                String tempUuid = imageStorageService.extractTempUuidFromPath(request.getThumbnailImageName());
                if (tempUuid != null && imageStorageService.isTempImage(request.getThumbnailImageName())) {
                    String finalThumbnailPath = imageStorageService.moveProductImageToFinal(
                            tempUuid,
                            product.getUuid(),
                            request.getThumbnailImageName()
                    );
                    product.setThumbnailUrl(toFullUrl(finalThumbnailPath));
                    log.info("Moved product thumbnail from temp to final: {}", finalThumbnailPath);
                }
            }

            // Save product gallery images and move them from temp to final folder
            if (request.getGalleryImages() != null && !request.getGalleryImages().isEmpty()) {
                saveProductGalleryWithImageMove(product.getId(), product.getUuid(), request.getGalleryImages());
            }

            // Save product meta (SEO)
            if (request.getMeta() != null) {
                saveProductMeta(product.getId(), request.getMeta());
            }

            // Save product dimension
            if (request.getDimension() != null) {
                saveProductDimension(product.getId(), request.getDimension());
            }

            // Save product tags
            if (request.getTagUuids() != null && !request.getTagUuids().isEmpty()) {
                saveProductTags(product.getId(), request.getTagUuids());
            }

            // Save product links
            if (request.getLinkedProducts() != null && !request.getLinkedProducts().isEmpty()) {
                saveProductLinks(product.getId(), request.getLinkedProducts());
            }

            // Save payment method mappings (resolved via RabbitMQ above)
            if (!resolvedPaymentMethods.isEmpty()) {
                saveProductPaymentMethodMappings(product.getId(), resolvedPaymentMethods);
            }

            // Save variants - CRITICAL: If this fails, entire transaction must rollback
            Long defaultVariantId = saveProductVariants(product, request.getVariants(), productType, request.getResellerPrice());

            // Update product with default variant ID
            product.setDefaultVariantId(defaultVariantId);
            product = productRepository.save(product);

            // Save price history
            savePriceHistory(product.getId(), null, null, product.getShowcasePrice());

            // Publish Product Created Event for POS Cache Sync and Inventory Module
            publishProductCreatedEvent(product, request.getVariants());

            log.info("Product creation completed successfully for SKU: {} with UUID: {}", request.getSku(), product.getUuid());

            return mapToProductResponse(product);

        } catch (Exception e) {
            log.error("Error during product creation for SKU: {}. Transaction will rollback. Error: {}",
                    request.getSku(), e.getMessage(), e);
            // Re-throw the exception to trigger @Transactional rollback
            throw e;
        }
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(String uuid, UpdateProductRequest request) {
        log.info("Updating product with UUID: {}", uuid);

        Product product = productRepository.findByUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with UUID: " + uuid));

        // Update slug if provided
        if (request.getSlug() != null && !request.getSlug().equals(product.getSlug())) {
            validateSlug(request.getSlug(), request.getTitle() != null ? request.getTitle() : product.getTitle());
            if (productRepository.existsActiveBySlug(request.getSlug())) {
                throw new BadRequestException("Product with slug '" + request.getSlug() + "' already exists");
            }
            product.setSlug(request.getSlug());
        }

        // Update category if provided
        if (request.getCategoryUuid() != null) {
            ProductCategory category = categoryRepository.findByUuid(request.getCategoryUuid())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found with UUID: " + request.getCategoryUuid()));
            product.setCategoryId(category.getId());
        }

        // Track price change
        BigDecimal oldPrice = product.getShowcasePrice();

        // Update product fields
        if (request.getTitle() != null) product.setTitle(request.getTitle());
        if (request.getShortDescription() != null) product.setShortDescription(request.getShortDescription());
        if (request.getDescription() != null) product.setDescription(request.getDescription());

        // Track if thumbnail is being updated
        boolean isThumbnailUpdated = request.getThumbnailImageName() != null;
        if (isThumbnailUpdated) product.setThumbnailUrl(toFullUrl(request.getThumbnailImageName()));

        if (request.getShowcasePrice() != null) product.setShowcasePrice(request.getShowcasePrice());
        if (request.getSalePrice() != null) product.setSalePrice(request.getSalePrice());
        // If either saleStart or saleEnd is null, clear both. Otherwise, set both values.
        if (request.getSaleStart() == null || request.getSaleEnd() == null) {
            product.setSaleStart(null);
            product.setSaleEnd(null);
        } else {
            product.setSaleStart(request.getSaleStart());
            product.setSaleEnd(request.getSaleEnd());
        }
        if (request.getFeatured() != null) product.setFeatured(request.getFeatured());
        if (request.getVisibility() != null) product.setVisibility(Product.ProductVisibility.valueOf(request.getVisibility().toUpperCase()));
        if (request.getSoldIndividually() != null) product.setSoldIndividually(request.getSoldIndividually());
        if (request.getPricingMode() != null) product.setPricingMode(Product.PricingMode.valueOf(request.getPricingMode().toUpperCase()));
        if (request.getWholesalePrice() != null) product.setWholesalePrice(request.getWholesalePrice());
        if (request.getWholesaleMinQty() != null) product.setWholesaleMinQty(request.getWholesaleMinQty());
        if (request.getProductionCost() != null) product.setProductionCost(request.getProductionCost());
        if (request.getInitialStockQuantity() != null) product.setInitialStockQuantity(request.getInitialStockQuantity());
        if (request.getInventoryStatus() != null) {
            product.setInventoryStatus(Product.InventoryStatus.valueOf(request.getInventoryStatus().toUpperCase()));
        }
        if (request.getIsPublish() != null) product.setIsPublish(request.getIsPublish());
        if (request.getIsResellerProduct() != null) product.setIsResellerProduct(request.getIsResellerProduct());

        product = productRepository.save(product);

        // Update default variant if product is SIMPLE and has a default variant
        if (product.getProductType() == Product.ProductType.SIMPLE && product.getDefaultVariantId() != null) {
            ProductVariant defaultVariant = variantRepository.findById(product.getDefaultVariantId()).orElse(null);
            if (defaultVariant != null) {
                boolean variantUpdated = false;

                // Update default variant thumbnail if product thumbnail was updated
                if (isThumbnailUpdated) {
                    defaultVariant.setThumbnailUrl(product.getThumbnailUrl());
                    variantUpdated = true;
                }

                // Update default variant sale dates if product sale dates were updated
                if (request.getSaleStart() != null || request.getSaleEnd() != null) {
                    defaultVariant.setSaleStart(product.getSaleStart());
                    defaultVariant.setSaleEnd(product.getSaleEnd());
                    variantUpdated = true;
                }

                if (variantUpdated) {
                    variantRepository.save(defaultVariant);
                    log.info("Updated default variant for SIMPLE product ID: {} (thumbnail={}, salesDates={})",
                            product.getId(), isThumbnailUpdated, request.getSaleStart() != null || request.getSaleEnd() != null);
                }
            }
        }

        // Update gallery images if provided
        if (request.getGalleryImages() != null) {
            updateProductGalleryUpdateOnly(product.getId(), request.getGalleryImages());
        }

        // Update meta if provided
        if (request.getMeta() != null) {
            updateProductMetaUpdateOnly(product.getId(), request.getMeta());
        }

        // Update dimension if provided
        if (request.getDimension() != null) {
            updateProductDimensionUpdateOnly(product.getId(), request.getDimension());
        }

        // Update tags if provided
        if (request.getTagUuids() != null) {
            updateProductTagsUpdateOnly(product.getId(), request.getTagUuids());
        }

        // Update linked products if provided
        if (request.getLinkedProducts() != null) {
            saveProductLinks(product.getId(), request.getLinkedProducts(), false);
        }

        // Update payment methods if provided
        if (request.getPaymentMethodUuids() != null) {
            updateProductPaymentMethodsUpdateOnly(product.getId(), request.getPaymentMethodUuids());
        }

        // Update variants if provided (update-only: will not create new variants)
        if (request.getVariants() != null && !request.getVariants().isEmpty()) {
            updateProductVariantsUpdateOnly(product.getId(), request.getVariants());
        }

        // Save price history if price changed
        if (oldPrice != null && !oldPrice.equals(product.getShowcasePrice())) {
            savePriceHistory(product.getId(), null, oldPrice, product.getShowcasePrice());

            // Publish Price Changed Event
            publishProductPriceChangedEvent(product.getId(), null, oldPrice, product.getShowcasePrice());
        }

        // Publish Product Updated Event
        publishProductUpdatedEvent(product, oldPrice);

        return mapToProductResponse(product);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductByUuid(String uuid) {
        log.info("Fetching product with UUID: {}", uuid);
        Product product = productRepository.findByUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with UUID: " + uuid));

        // Get the basic product response
        ProductResponse productResponse = mapToProductResponse(product);

        // Add the variant attributes map (single value per attribute)
        Map<String, Map<String, String>> variantAttributesMap = getProductAttributesMap(uuid);
        productResponse.setVariantAttributesMap(variantAttributesMap);

        // Add the variant attributes list map (list of values per attribute)
        Map<String, Map<String, List<String>>> variantAttributesListMap = getProductAttributesListMap(uuid);
        productResponse.setVariantAttributesListMap(variantAttributesListMap);

        // Attach payment method names allowed for this product
        List<String> paymentMethodNames = resolveProductPaymentMethodNames(product.getId());
        productResponse.setPaymentMethods(paymentMethodNames);

        return productResponse;
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductBySlug(String slug) {
        log.info("Fetching product with slug: {}", slug);
        Product product = productRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with slug: " + slug));

        // Get the basic product response
        ProductResponse productResponse = mapToProductResponse(product);

        // Add the variant attributes map (single value per attribute)
        Map<String, Map<String, String>> variantAttributesMap = getProductAttributesMap(product.getUuid());
        productResponse.setVariantAttributesMap(variantAttributesMap);

        // Add the variant attributes list map (list of values per attribute)
        Map<String, Map<String, List<String>>> variantAttributesListMap = getProductAttributesListMap(product.getUuid());
        productResponse.setVariantAttributesListMap(variantAttributesListMap);

        // Attach payment method names allowed for this product
        List<String> paymentMethodNames = resolveProductPaymentMethodNames(product.getId());
        productResponse.setPaymentMethods(paymentMethodNames);

        return productResponse;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> getAllProducts(Pageable pageable) {
        log.info("Fetching all products - page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSize());
        Page<Product> productPage = productRepository.findAll(pageable);
        return mapToPageResponse(productPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> getPublishedProducts(Pageable pageable) {
        log.info("Fetching published products");
        Page<Product> productPage = productRepository.findAll(pageable);
        // Filter published products
        List<Product> publishedProducts = productPage.getContent().stream()
                .filter(p -> p.getIsPublish() && p.getIsActive() && p.getVisibility() == Product.ProductVisibility.PUBLIC)
                .collect(Collectors.toList());

        return PageResponse.<ProductResponse>builder()
                .content(publishedProducts.stream().map(this::mapToProductResponse).collect(Collectors.toList()))
                .pageNumber(productPage.getNumber())
                .pageSize(productPage.getSize())
                .totalElements((long) publishedProducts.size())
                .totalPages(productPage.getTotalPages())
                .last(productPage.isLast())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(
            value = "productCards",
            key = "#pageable.pageNumber + '-' + #pageable.pageSize + '-' + #pageable.sort.toString()"
    )
    public PageResponse<ProductCardResponse> getPublishedProductCards(Pageable pageable) {
        log.info("Fetching published product cards for website display (Cache MISS or expired)");
        Page<Product> productPage = productRepository.findAll(pageable);

        // Filter published products
        List<Product> publishedProducts = productPage.getContent().stream()
                .filter(p -> p.getIsPublish() && p.getIsActive() && p.getVisibility() == Product.ProductVisibility.PUBLIC)
                .collect(Collectors.toList());

        // Batch fetch ratings for all products in one query (avoiding N+1)
        List<Long> productIds = publishedProducts.stream()
                .map(Product::getId)
                .collect(Collectors.toList());

        Map<Long, ProductRatingAggregate> ratingsMap = new java.util.HashMap<>();
        if (!productIds.isEmpty()) {
            List<ProductRatingAggregate> ratings = ratingAggregateRepository.findAllById(productIds);
            ratingsMap = ratings.stream()
                    .collect(Collectors.toMap(
                            ProductRatingAggregate::getProductId,
                            rating -> rating
                    ));
        }

        // Map to product card responses with ratings (avoiding N+1 queries)
        final Map<Long, ProductRatingAggregate> finalRatingsMap = ratingsMap;
        List<ProductCardResponse> productCards = publishedProducts.stream()
                .map(product -> mapToProductCardResponse(product, finalRatingsMap.get(product.getId())))
                .collect(Collectors.toList());

        log.info("Product cards fetched and cached: {} products", productCards.size());

        return PageResponse.<ProductCardResponse>builder()
                .content(productCards)
                .pageNumber(productPage.getNumber())
                .pageSize(productPage.getSize())
                .totalElements((long) publishedProducts.size())
                .totalPages(productPage.getTotalPages())
                .last(productPage.isLast())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductFilterResponse> getAllProductsForFilter(Pageable pageable) {
        log.info("Fetching all products for filter - page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSize());

        // Get all products with pagination
        Page<Product> productPage = productRepository.findAll(pageable);

        // Filter only published and active products
        List<Product> products = productPage.getContent().stream()
                .filter(p -> p.getIsPublish() && p.getIsActive())
                .collect(Collectors.toList());

        // Batch fetch ratings for all products (avoiding N+1)
        List<Long> productIds = products.stream().map(Product::getId).collect(Collectors.toList());
        Map<Long, ProductRatingAggregate> ratingsMap = new java.util.HashMap<>();
        if (!productIds.isEmpty()) {
            List<ProductRatingAggregate> ratings = ratingAggregateRepository.findAllById(productIds);
            ratingsMap = ratings.stream()
                    .collect(Collectors.toMap(ProductRatingAggregate::getProductId, rating -> rating));
        }

        // Batch fetch variants for all products (avoiding N+1)
        Map<Long, List<ProductVariant>> variantsMap = new java.util.HashMap<>();
        if (!productIds.isEmpty()) {
            List<ProductVariant> variants = variantRepository.findByProductIdIn(productIds);
            variantsMap = variants.stream()
                    .collect(Collectors.groupingBy(ProductVariant::getProductId));
        }

        // Map to filter responses
        final Map<Long, ProductRatingAggregate> finalRatingsMap = ratingsMap;
        final Map<Long, List<ProductVariant>> finalVariantsMap = variantsMap;

        List<ProductFilterResponse> filterResponses = products.stream()
                .map(product -> mapToProductFilterResponse(
                        product,
                        finalRatingsMap.get(product.getId()),
                        finalVariantsMap.getOrDefault(product.getId(), List.of())
                ))
                .collect(Collectors.toList());

        log.info("Products for filter fetched: {} products", filterResponses.size());

        return PageResponse.<ProductFilterResponse>builder()
                .content(filterResponses)
                .pageNumber(productPage.getNumber())
                .pageSize(productPage.getSize())
                .totalElements(productPage.getTotalElements())
                .totalPages(productPage.getTotalPages())
                .last(productPage.isLast())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> getFeaturedProducts(Pageable pageable) {
        log.info("Fetching featured products");
        List<Product> featuredProducts = productRepository.findFeaturedProducts();
        return PageResponse.<ProductResponse>builder()
                .content(featuredProducts.stream().map(this::mapToProductResponse).collect(Collectors.toList()))
                .pageNumber(0)
                .pageSize(featuredProducts.size())
                .totalElements((long) featuredProducts.size())
                .totalPages(1)
                .last(true)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> getProductsByCategory(String categoryUuid, Pageable pageable) {
        log.info("Fetching products for category UUID: {}", categoryUuid);
        ProductCategory category = categoryRepository.findByUuid(categoryUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with UUID: " + categoryUuid));

        List<Product> products = productRepository.findByCategoryId(category.getId());
        return PageResponse.<ProductResponse>builder()
                .content(products.stream().map(this::mapToProductResponse).collect(Collectors.toList()))
                .pageNumber(0)
                .pageSize(products.size())
                .totalElements((long) products.size())
                .totalPages(1)
                .last(true)
                .build();
    }

    @Override
    @Transactional
    public void deleteProduct(String uuid) {
        log.info("Deleting product with UUID: {}", uuid);

        // Validation - Check if product already deleted (idempotency)
        Product product = productRepository.findByUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with UUID: " + uuid));

        if (!product.getIsActive()) {
            log.warn("Product already marked as inactive: UUID={}, ProductID={}", uuid, product.getId());
            return;
        }

        // Get product using product UUID and set is_active=false
        product.setIsActive(false);
        productRepository.saveAndFlush(product);
        log.info("Product marked as inactive: UUID={}, ProductID={}, Title={}", uuid, product.getId(), product.getTitle());

        // Get all product_variants where product_id matches and set is_active=false
        List<ProductVariant> variants = variantRepository.findByProductId(product.getId());

        if (!variants.isEmpty()) {
            variants.forEach(variant -> {
                variant.setIsActive(false);
                log.debug("Marking variant as inactive: VariantID={}, SKU={}, AttributeSummary={}",
                        variant.getId(), variant.getSku(), variant.getAttributeSummary());
            });
            variantRepository.saveAll(variants);
            log.info("Marked {} product variants as inactive for product ID: {}", variants.size(), product.getId());
        } else {
            log.warn("No variants found for product ID: {}", product.getId());
        }

        // Mark related variant-level data as inactive
        markRelatedVariantDataInactive(product.getId(), variants);

        // Mark product-level related data as inactive
        markRelatedProductDataInactive(product.getId());

        log.info("Publishing ProductDeletedEvent to notify Inventory Module to mark product stocks as inactive for ProductID={}", product.getId());

        publishProductDeletedEvent(product.getId(), product.getUuid());

        log.info("Product deletion completed successfully: UUID={}, ProductID={}, Title={}, VariantsMarkedInactive={}",
                uuid, product.getId(), product.getTitle(), variants.size());
    }

    /**
     * Mark related variant-level data as inactive (junction tables)
     * This includes: variant attribute values, variant galleries
     */
    private void markRelatedVariantDataInactive(Long productId, List<ProductVariant> variants) {
        if (variants.isEmpty()) {
            return;
        }

        try {
            // Mark variant attribute values as inactive
            for (ProductVariant variant : variants) {
                List<VariantAttributeValue> variantAttributeValues = variantAttributeValueRepository.findByVariantId(variant.getId());
                for (VariantAttributeValue vav : variantAttributeValues) {
                    vav.setIsActive(false);
                    variantAttributeValueRepository.save(vav);
                }

                // Mark variant gallery images as inactive
                List<VariantGallery> variantGalleries = variantGalleryRepository.findByVariantIdOrderBySortOrder(variant.getId());
                for (VariantGallery vg : variantGalleries) {
                    vg.setIsActive(false);
                    variantGalleryRepository.save(vg);
                }
            }
            log.info("Marked related variant data (attributes, galleries) as inactive for product ID: {}", productId);
        } catch (Exception e) {
            log.error("Error marking related variant data as inactive for product ID: {}", productId, e);
        }
    }

    /**
     * Mark related product-level data as inactive
     * This includes: product gallery, meta, dimension, tags, links, payment methods, price history
     */
    private void markRelatedProductDataInactive(Long productId) {
        try {
            // Mark product gallery images as inactive
            List<ProductGallery> galleries = galleryRepository.findByProductIdAndIsActiveTrueOrderBySortOrderAsc(productId);
            for (ProductGallery gallery : galleries) {
                gallery.setIsActive(false);
                galleryRepository.save(gallery);
            }

            // Mark product meta as inactive
            metaRepository.findByProductId(productId).ifPresent(meta -> {
                meta.setIsActive(false);
                metaRepository.save(meta);
            });

            // Mark product dimension as inactive
            dimensionRepository.findByProductId(productId).ifPresent(dimension -> {
                dimension.setIsActive(false);
                dimensionRepository.save(dimension);
            });

            // Mark product tags (tag mappings) - soft delete via flag
            List<ProductTagMap> tagMaps = tagMapRepository.findByProductId(productId);
            if (!tagMaps.isEmpty()) {
                tagMapRepository.deleteAll(tagMaps);  // Bulk delete - more efficient
            }

            // Mark product links as inactive
            List<ProductLink> links = productLinkRepository.findByProductId(productId);
            for (ProductLink link : links) {
                link.setIsActive(false);
                productLinkRepository.save(link);
            }

            // Mark payment method mappings as inactive
            List<ProductPaymentMethodMapping> paymentMappings = paymentMethodMappingRepository.findByProductIdAndIsActive(productId, true);
            for (ProductPaymentMethodMapping mapping : paymentMappings) {
                mapping.setIsActive(false);
                paymentMethodMappingRepository.save(mapping);
            }

            // Mark price history as inactive
            List<ProductPriceHistory> priceHistories = priceHistoryRepository.findByProductIdOrderByChangedAtDesc(productId);
            for (ProductPriceHistory history : priceHistories) {
                history.setIsActive(false);
                priceHistoryRepository.save(history);
            }

            log.info("Marked all related product data (galleries, meta, dimension, links, payment methods, price history) as inactive for product ID: {}", productId);
        } catch (Exception e) {
            log.error("Error marking related product data as inactive for product ID: {}", productId, e);
        }
    }

    @Override
    public ProductResponse publishProduct(String uuid) {
        log.info("Publishing product with UUID: {}", uuid);
        Product product = productRepository.findByUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with UUID: " + uuid));

        product.setIsPublish(true);
        product = productRepository.save(product);
        return mapToProductResponse(product);
    }

    @Override
    public ProductResponse unpublishProduct(String uuid) {
        log.info("Unpublishing product with UUID: {}", uuid);
        Product product = productRepository.findByUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with UUID: " + uuid));

        product.setIsPublish(false);
        product = productRepository.save(product);
        return mapToProductResponse(product);
    }

    @Override
    @Transactional(readOnly = true)
    public ImageThumbnailUrlResponse getVariantGalleryImages(String variantUuid) {
        log.info("Fetching variant gallery images for variant UUID: {}", variantUuid);

        // Validate variant exists in product_variants table
        ProductVariant variant = variantRepository.findByUuid(variantUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found with UUID: " + variantUuid));

        // Return thumbnail_url from product_variants table
        String thumbnailUrl = variant.getThumbnailUrl();

        log.info("Found thumbnail URL for variant {}: {}", variantUuid, thumbnailUrl);

        return ImageThumbnailUrlResponse.builder()
                .thumbnailUrl(thumbnailUrl)
                .build();
    }

    private void saveProductLinks(Long productId, List<ProductLinkRequest> linkRequests) {
        saveProductLinks(productId, linkRequests, true);
    }

    /**
     * Save or update product links
     * In update-only mode (allowCreate=false), this method will NOT insert new rows.
     */
    private void saveProductLinks(Long productId, List<ProductLinkRequest> linkRequests, boolean allowCreate) {
        // Get all existing links for this product
        List<ProductLink> existingLinks = productLinkRepository.findByProductId(productId);

        // Track which links are in the request
        java.util.Set<String> requestedLinkKeys = new java.util.HashSet<>();

        for (ProductLinkRequest linkRequest : linkRequests) {
            // Validate linked product exists
            Product linkedProduct = productRepository.findByUuid(linkRequest.getLinkedProductUuid())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Linked product not found with UUID: " + linkRequest.getLinkedProductUuid()));

            // Validate link type
            ProductLink.LinkType linkType;
            try {
                linkType = ProductLink.LinkType.valueOf(linkRequest.getLinkType().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid link type. Must be UPSELL, CROSSSELL, or RELATED");
            }

            // Create a unique key for this link combination
            String linkKey = productId + ":" + linkedProduct.getId() + ":" + linkType.name();
            requestedLinkKeys.add(linkKey);

            // Check if link already exists
            java.util.Optional<ProductLink> existingLinkOpt = existingLinks.stream()
                    .filter(link -> link.getProductId().equals(productId)
                            && link.getLinkedId().equals(linkedProduct.getId())
                            && link.getLinkType().equals(linkType))
                    .findFirst();

            if (existingLinkOpt.isPresent()) {
                // Update existing link - reactivate if it was inactive
                ProductLink existingLink = existingLinkOpt.get();
                if (!existingLink.getIsActive()) {
                    existingLink.setIsActive(true);
                    productLinkRepository.save(existingLink);
                    log.info("Reactivated existing product link: Product {} -> {} ({})",
                            productId, linkedProduct.getId(), linkType);
                } else {
                    log.info("Product link already exists and is active: Product {} -> {} ({})",
                            productId, linkedProduct.getId(), linkType);
                }
            } else {
                if (!allowCreate) {
                    throw new BadRequestException(
                            "Update cannot create new product link. Link does not exist: linkedProductUuid=" +
                                    linkRequest.getLinkedProductUuid() + ", linkType=" + linkType.name());
                }

                // Create new link (create flow only)
                ProductLink link = ProductLink.builder()
                        .productId(productId)
                        .linkedId(linkedProduct.getId())
                        .linkType(linkType)
                        .isActive(true)
                        .build();
                productLinkRepository.save(link);
                log.info("Created new product link: Product {} -> {} ({})",
                        productId, linkedProduct.getId(), linkType);
            }
        }

        // Soft delete any existing links that are not in the request
        for (ProductLink existingLink : existingLinks) {
            String linkKey = existingLink.getProductId() + ":" + existingLink.getLinkedId() + ":" + existingLink.getLinkType().name();
            if (!requestedLinkKeys.contains(linkKey) && existingLink.getIsActive()) {
                existingLink.setIsActive(false);
                productLinkRepository.save(existingLink);
                log.info("Soft deleted product link: Product {} -> {} ({})",
                        existingLink.getProductId(), existingLink.getLinkedId(), existingLink.getLinkType());
            }
        }

        log.info("Updated {} product links for product ID: {}", linkRequests.size(), productId);
    }

    /**
     * UPDATE-ONLY gallery update:
     * - does NOT insert new rows
     * - uses sortOrder as the stable key (request DTO has no UUID)
     * - reactivates requested sortOrders, updates their fields
     * - deactivates missing sortOrders
     */
    private void updateProductGalleryUpdateOnly(Long productId, List<ProductGalleryRequest> galleryRequests) {
        List<ProductGallery> existing = galleryRepository.findByProductIdAndIsActiveTrueOrderBySortOrderAsc(productId);
        Map<Integer, ProductGallery> existingBySortOrder = existing.stream()
                .collect(Collectors.toMap(
                        ProductGallery::getSortOrder,
                        g -> g,
                        (a, b) -> a
                ));

        Set<Integer> requestedSortOrders = new HashSet<>();

        // Update/reactivate requested rows
        for (ProductGalleryRequest req : galleryRequests) {
            if (req.getSortOrder() == null) {
                throw new BadRequestException("Gallery sortOrder is required for update");
            }
            if (!requestedSortOrders.add(req.getSortOrder())) {
                throw new BadRequestException("Duplicate gallery sortOrder in request: " + req.getSortOrder());
            }

            ProductGallery gallery = existingBySortOrder.get(req.getSortOrder());
            if (gallery == null) {
                // Create new gallery if it doesn't exist
                if (req.getMediaName() == null || req.getMediaName().trim().isEmpty()) {
                    throw new BadRequestException("Gallery mediaName is required for new gallery (sortOrder=" + req.getSortOrder() + ")");
                }
                gallery = ProductGallery.builder()
                        .productId(productId)
                        .sortOrder(req.getSortOrder())
                        .mediaName(toFullUrl(req.getMediaName()))
                        .mediaType(ProductGallery.MediaType.IMAGE)
                        .altText(req.getAltText())
                        .mimeType(req.getMimeType())
                        .isActive(true)
                        .build();
            } else {
                // Update existing gallery
                if (req.getMediaName() != null) {
                    if (req.getMediaName().trim().isEmpty()) {
                        throw new BadRequestException("Gallery mediaName cannot be blank (sortOrder=" + req.getSortOrder() + ")");
                    }
                    gallery.setMediaName(toFullUrl(req.getMediaName()));
                }
                if (req.getAltText() != null) {
                    gallery.setAltText(req.getAltText());
                }
                if (req.getMimeType() != null) {
                    gallery.setMimeType(req.getMimeType());
                }
                gallery.setIsActive(true);
            }
            galleryRepository.save(gallery);
        }

        // Deactivate any existing galleries not in request
        for (ProductGallery gallery : existing) {
            if (!requestedSortOrders.contains(gallery.getSortOrder()) && Boolean.TRUE.equals(gallery.getIsActive())) {
                gallery.setIsActive(false);
                galleryRepository.save(gallery);
            }
        }

        log.info("Gallery update-only completed for product ID: {} (requested={}, existing={})",
                productId, galleryRequests.size(), existing.size());
    }

    /**
     * UPDATE-ONLY meta update: does not create meta row.
     */
    private void updateProductMetaUpdateOnly(Long productId, ProductMetaRequest metaRequest) {
        ProductMeta meta = metaRepository.findByProductId(productId)
                .orElseThrow(() -> new BadRequestException(
                        "Update cannot create meta. Meta does not exist for product ID: " + productId));

        if (metaRequest.getMetaTitle() != null) meta.setMetaTitle(metaRequest.getMetaTitle());
        if (metaRequest.getMetaDescription() != null) meta.setMetaDescription(metaRequest.getMetaDescription());
        if (metaRequest.getCanonicalUrl() != null) meta.setCanonicalUrl(metaRequest.getCanonicalUrl());
        if (metaRequest.getMetaKeywords() != null) meta.setMetaKeywords(metaRequest.getMetaKeywords());
        if (metaRequest.getOgTitle() != null) meta.setOgTitle(metaRequest.getOgTitle());
        if (metaRequest.getOgDescription() != null) meta.setOgDescription(metaRequest.getOgDescription());
        if (metaRequest.getOgImage() != null) meta.setOgImage(metaRequest.getOgImage());
        if (metaRequest.getTwitterCard() != null) meta.setTwitterCard(metaRequest.getTwitterCard());
        if (metaRequest.getJsonLd() != null) meta.setJsonLd(metaRequest.getJsonLd());
        if (metaRequest.getRobotIndex() != null) meta.setRobotIndex(metaRequest.getRobotIndex());

        metaRepository.save(meta);
    }

    /**
     * UPDATE-ONLY dimension update: does not create dimension row.
     */
    private void updateProductDimensionUpdateOnly(Long productId, ProductDimensionRequest dimensionRequest) {
        ProductDimension dimension = dimensionRepository.findByProductId(productId)
                .orElseThrow(() -> new BadRequestException(
                        "Update cannot create dimension. Dimension does not exist for product ID: " + productId));

        if (dimensionRequest.getWeightKg() != null) dimension.setWeightKg(dimensionRequest.getWeightKg());
        if (dimensionRequest.getLengthCm() != null) dimension.setLengthCm(dimensionRequest.getLengthCm());
        if (dimensionRequest.getWidthCm() != null) dimension.setWidthCm(dimensionRequest.getWidthCm());
        if (dimensionRequest.getHeightCm() != null) dimension.setHeightCm(dimensionRequest.getHeightCm());

        dimensionRepository.save(dimension);
    }

    /**
     * UPDATE-ONLY tag update:
     * - does NOT create new tag mappings
     * - allows removing mappings (delete rows)
     */
    private void updateProductTagsUpdateOnly(Long productId, List<String> tagUuids) {
        List<ProductTagMap> existingMaps = tagMapRepository.findByProductId(productId);
        Set<Long> existingTagIds = existingMaps.stream()
                .map(ProductTagMap::getTagId)
                .collect(Collectors.toSet());

        Set<Long> requestedTagIds = new HashSet<>();
        for (String tagUuid : tagUuids) {
            ProductTag tag = tagRepository.findByUuid(tagUuid)
                    .orElseThrow(() -> new ResourceNotFoundException("Tag not found with UUID: " + tagUuid));
            requestedTagIds.add(tag.getId());

            if (!existingTagIds.contains(tag.getId())) {
                throw new BadRequestException(
                        "Update cannot create new tag mapping. Tag not currently mapped to product: " + tagUuid);
            }
        }

        // Remove mappings that are not requested
        List<ProductTagMap> toDelete = existingMaps.stream()
                .filter(m -> !requestedTagIds.contains(m.getTagId()))
                .collect(Collectors.toList());
        if (!toDelete.isEmpty()) {
            tagMapRepository.deleteAll(toDelete);
        }

        log.info("Tags update-only completed for product ID: {} (kept={}, removed={})",
                productId, requestedTagIds.size(), toDelete.size());
    }

    /**
     * UPDATE-ONLY payment method update:
     * - Resolves payment method UUIDs via RabbitMQ
     * - Deletes all existing payment method mappings for the product
     * - Saves new payment method mappings
     */
    private void updateProductPaymentMethodsUpdateOnly(Long productId, List<String> paymentMethodUuids) {
        if (paymentMethodUuids == null || paymentMethodUuids.isEmpty()) {
            return;
        }

        log.info("Updating payment methods for product ID: {} with {} UUIDs", productId, paymentMethodUuids.size());

        // Resolve payment method details via RabbitMQ
        List<Map<String, Object>> resolvedPaymentMethods = fetchPaymentMethodDetails(paymentMethodUuids);
        if (resolvedPaymentMethods.isEmpty()) {
            throw new BadRequestException("None of the provided payment method UUIDs could be resolved. Please check the UUIDs.");
        }

        log.info("Resolved {}/{} payment methods for product update",
                resolvedPaymentMethods.size(), paymentMethodUuids.size());

        // Delete old payment method mappings - fetch and delete individually for better transaction handling
        List<ProductPaymentMethodMapping> existingMappings = paymentMethodMappingRepository.findByProductIdAndIsActive(productId, true);
        if (!existingMappings.isEmpty()) {
            paymentMethodMappingRepository.deleteAll(existingMappings);
            log.debug("Deleted {} existing payment method mappings for product ID: {}", existingMappings.size(), productId);
        }

        // Save new payment method mappings
        saveProductPaymentMethodMappings(productId, resolvedPaymentMethods);

        log.info("Payment methods update-only completed for product ID: {} (count={})",
                productId, resolvedPaymentMethods.size());
    }

    private void updateProductVariantsUpdateOnly(Long productId, List<ProductVariantRequest> variantRequests) {
        if (variantRequests == null || variantRequests.isEmpty()) {
            return;
        }

        for (ProductVariantRequest variantRequest : variantRequests) {
            // In update-only mode, uuid is required to identify which variant to update
            if (variantRequest.getUuid() == null || variantRequest.getUuid().isEmpty()) {
                throw new BadRequestException("Update cannot create new variant. Variant UUID is required for update.");
            }

            // Find existing variant by UUID
            ProductVariant variant = variantRepository.findByUuid(variantRequest.getUuid())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Variant not found with UUID: " + variantRequest.getUuid()));

            // Ensure variant belongs to this product
            if (!variant.getProductId().equals(productId)) {
                throw new BadRequestException(
                        "Variant UUID " + variantRequest.getUuid() + " does not belong to product ID " + productId);
            }

            // Track showcase price change for events
            BigDecimal oldShowcasePrice = variant.getShowcasePrice();

            // Update SKU if provided and different
            if (variantRequest.getSku() != null && !variantRequest.getSku().equals(variant.getSku())) {
                // Check if new SKU already exists (except for this variant, only active variants)
                if (variantRepository.existsActiveBySku(variantRequest.getSku())) {
                    throw new BadRequestException("Variant with SKU '" + variantRequest.getSku() + "' already exists");
                }
                variant.setSku(variantRequest.getSku());
                log.debug("Updated variant SKU: {} -> {}", variant.getSku(), variantRequest.getSku());
            }

            // Update dimensions if provided
            if (variantRequest.getWeightKg() != null) {
                variant.setWeightKg(variantRequest.getWeightKg());
            }
            if (variantRequest.getLengthCm() != null) {
                variant.setLengthCm(variantRequest.getLengthCm());
            }
            if (variantRequest.getWidthCm() != null) {
                variant.setWidthCm(variantRequest.getWidthCm());
            }
            if (variantRequest.getHeightCm() != null) {
                variant.setHeightCm(variantRequest.getHeightCm());
            }

            // Update prices if provided
            if (variantRequest.getShowcasePrice() != null) {
                variant.setShowcasePrice(variantRequest.getShowcasePrice());
            }
            if (variantRequest.getSalePrice() != null) {
                variant.setSalePrice(variantRequest.getSalePrice());
            }
            if (variantRequest.getResellerPrice() != null) {
                variant.setResellerPrice(variantRequest.getResellerPrice());
            }
            if (variantRequest.getWholesalePrice() != null) {
                variant.setWholesalePrice(variantRequest.getWholesalePrice());
            }
            if (variantRequest.getWholesaleMinQty() != null) {
                variant.setWholesaleMinQty(variantRequest.getWholesaleMinQty());
            }
            if (variantRequest.getProductionCost() != null) {
                variant.setProductionCost(variantRequest.getProductionCost());
            }

            // Update sale dates if provided - if either is null, clear both. Otherwise, set both values.
            if (variantRequest.getSaleStart() == null || variantRequest.getSaleEnd() == null) {
                variant.setSaleStart(null);
                variant.setSaleEnd(null);
            } else {
                variant.setSaleStart(variantRequest.getSaleStart());
                variant.setSaleEnd(variantRequest.getSaleEnd());
            }

            // Update inventory status if provided
            if (variantRequest.getInventoryStatus() != null) {
                ProductVariant.InventoryStatus status = ProductVariant.InventoryStatus.valueOf(
                        variantRequest.getInventoryStatus().toUpperCase());
                variant.setInventoryStatus(status);
            }

            // Update thumbnail/image if provided
            if (variantRequest.getThumbnailImageName() != null) {
                String finalThumbnailPath = variantRequest.getThumbnailImageName();

                // Move image from temp to final folder if it's a temp image
                String tempUuid = imageStorageService.extractTempUuidFromPath(variantRequest.getThumbnailImageName());
                if (tempUuid != null && imageStorageService.isTempImage(variantRequest.getThumbnailImageName())) {
                    Product parentProduct = productRepository.findById(productId).orElseThrow(
                            () -> new ResourceNotFoundException("Product not found with ID: " + productId));
                    finalThumbnailPath = imageStorageService.moveProductImageToFinal(
                            tempUuid,
                            parentProduct.getUuid(),
                            variantRequest.getThumbnailImageName()
                    );
                    log.info("Moved variant thumbnail from temp to final for variant UUID {}: {}",
                            variant.getUuid(), finalThumbnailPath);
                }

                variant.setThumbnailUrl(toFullUrl(finalThumbnailPath));
            }

            // Save the updated variant
            variant = variantRepository.save(variant);
            log.info("Updated variant with UUID: {} and SKU: {}", variant.getUuid(), variant.getSku());

            // Track price change and publish event if showcase price changed
            if (oldShowcasePrice != null && !oldShowcasePrice.equals(variant.getShowcasePrice())) {
                savePriceHistory(productId, variant.getId(), oldShowcasePrice, variant.getShowcasePrice());
                publishProductPriceChangedEvent(productId, variant.getId(), oldShowcasePrice, variant.getShowcasePrice());
                log.info("Tracked price change for variant UUID: {} ({} -> {})",
                        variant.getUuid(), oldShowcasePrice, variant.getShowcasePrice());
            }
        }

        log.info("Variant update-only completed for product ID: {} (updated={} variants)",
                productId, variantRequests.size());
    }

    // ========================================
    // EVENT PUBLISHING METHODS
    // ========================================

    private void publishProductCreatedEvent(Product product, List<ProductVariantRequest> variantRequests) {
        ProductPosSnapshot snapshot = buildProductPosSnapshot(product, variantRequests);
        ProductCreatedEvent event = ProductCreatedEvent.builder()
                .productId(product.getId())
                .uuid(product.getUuid())
                .sku(product.getSku())
                .title(product.getTitle())
                .slug(product.getSlug())
                .categoryId(product.getCategoryId())
                .productType(product.getProductType().name())
                .showcasePrice(snapshot.showcasePrice())
                .salePrice(snapshot.salePrice())
                .initialStockQuantity(product.getInitialStockQuantity())
                .stockAvailable(snapshot.stockAvailable())
                .thumbnailUrl(product.getThumbnailUrl())
                .hasVariants(snapshot.hasVariants())
                .createdBy("SYSTEM")
                .createdAt(LocalDateTime.now())
                .build();

        publishCreatedEventToRabbit(event, product.getUuid());
        posProductSyncService.processProductCreated(event, null);
        log.info("Synced product create to POS cache for product: {}", product.getUuid());
    }

    private void publishProductUpdatedEvent(Product product, BigDecimal oldPrice) {
        ProductPosSnapshot snapshot = buildProductPosSnapshot(product, null);
        ProductUpdatedEvent event = ProductUpdatedEvent.builder()
                .productId(product.getId())
                .uuid(product.getUuid())
                .sku(product.getSku())
                .title(product.getTitle())
                .oldPrice(oldPrice)
                .newPrice(snapshot.showcasePrice())
                .salePrice(snapshot.salePrice())
                .stockAvailable(snapshot.stockAvailable())
                .thumbnailUrl(product.getThumbnailUrl())
                .updatedBy("SYSTEM")
                .updatedAt(LocalDateTime.now())
                .build();

        publishUpdatedEventToRabbit(event, product.getUuid());
        posProductSyncService.processProductUpdated(event, null);
        log.info("Synced product update to POS cache for product: {}", product.getUuid());
    }

    private ProductPosSnapshot buildProductPosSnapshot(Product product, List<ProductVariantRequest> variantRequests) {
        List<ProductVariant> variants = variantRepository.findByProductIdAndIsActive(product.getId(), true);
        boolean hasVariants = !variants.isEmpty();
        BigDecimal showcasePrice = product.getShowcasePrice();
        BigDecimal salePrice = product.getSalePrice();
        int totalStock = product.getInitialStockQuantity() != null ? product.getInitialStockQuantity() : 0;

        if (hasVariants && product.getProductType() == Product.ProductType.VARIABLE) {
            ProductVariant firstVariant = variants.get(0);
            if (firstVariant.getShowcasePrice() != null) {
                showcasePrice = firstVariant.getShowcasePrice();
            }
            if (firstVariant.getSalePrice() != null) {
                salePrice = firstVariant.getSalePrice();
            }
            totalStock = variantRequests != null && !variantRequests.isEmpty()
                    ? variantRequests.stream()
                    .mapToInt(v -> v.getInitialStockQuantity() != null ? v.getInitialStockQuantity() : 0)
                    .sum()
                    : totalStock;
        }

        return new ProductPosSnapshot(showcasePrice, salePrice, totalStock, hasVariants);
    }


    private void publishProductDeletedEvent(Long productId, String uuid) {
        ProductDeletedEvent event = new ProductDeletedEvent(productId, uuid);
        publishDeletedEventToRabbit(productId, uuid);
        posProductSyncService.processProductDeleted(event, null);
        log.info("Synced product delete to POS cache for product: {}", uuid);
    }

    private void publishCreatedEventToRabbit(ProductCreatedEvent event, String productUuid) {
        try {
            eventPublisher.publishProductCreated(event);
            log.info("Published ProductCreatedEvent for product: {}", productUuid);
        } catch (Exception e) {
            log.error("Error publishing ProductCreatedEvent for product: {}", productUuid, e);
        }
    }

    private void publishUpdatedEventToRabbit(ProductUpdatedEvent event, String productUuid) {
        try {
            eventPublisher.publishProductUpdated(event);
            log.info("Published ProductUpdatedEvent for product: {}", productUuid);
        } catch (Exception e) {
            log.error("Error publishing ProductUpdatedEvent for product: {}", productUuid, e);
        }
    }

    private void publishDeletedEventToRabbit(Long productId, String uuid) {
        try {
            eventPublisher.publishProductDeleted(productId, uuid);
            log.info("Published ProductDeletedEvent for product: {}", uuid);
        } catch (Exception e) {
            log.error("Error publishing ProductDeletedEvent for product: {}", uuid, e);
        }
    }
    private record ProductPosSnapshot(BigDecimal showcasePrice, BigDecimal salePrice, Integer stockAvailable, Boolean hasVariants) {
    }

    private void publishProductPriceChangedEvent(Long productId, Long variantId, BigDecimal oldPrice, BigDecimal newPrice) {
        try {
            ProductPriceChangedEvent event = ProductPriceChangedEvent.builder()
                    .productId(productId)
                    .variantId(variantId)
                    .oldPrice(oldPrice)
                    .newPrice(newPrice)
                    .changedBy("SYSTEM")
                    .changedAt(LocalDateTime.now())
                    .build();

            eventPublisher.publishProductPriceChanged(event);
            log.info("Published ProductPriceChangedEvent for product: {}", productId);
        } catch (Exception e) {
            log.error("Error publishing ProductPriceChangedEvent for product: {}", productId, e);
        }
    }

    private void publishVariantCreatedEvent(ProductVariant variant, ProductVariantRequest request) {
        try {
            org.psint.beyosclothing.modules.products.events.VariantCreatedEvent event =
                    org.psint.beyosclothing.modules.products.events.VariantCreatedEvent.builder()
                            .variantId(variant.getId())
                            .uuid(variant.getUuid())
                            .productId(variant.getProductId())
                            .sku(variant.getSku())
                            .attributeSummary(variant.getAttributeSummary())
                            .initialStockQuantity(request.getInitialStockQuantity() != null ? request.getInitialStockQuantity() : 0)
                            .allowBackorder(request.getAllowBackorder() != null ? request.getAllowBackorder() : false)
                            .lowStockThreshold(request.getLowStockThreshold() != null ? request.getLowStockThreshold() : 10)
                            .createdBy("SYSTEM")
                            .createdAt(LocalDateTime.now())
                            .build();

            eventPublisher.publishVariantCreated(event);
            log.info("Published VariantCreatedEvent for variant: {} (Product: {})", variant.getUuid(), variant.getProductId());
        } catch (Exception e) {
            log.error("Error publishing VariantCreatedEvent for variant: {}", variant.getUuid(), e);
        }
    }

    // ========================================
    // HELPER METHODS
    // ========================================
    /**
     * Process attribute selections - create new attributes/values if flagged as new
     * This method ensures all attributes and values exist before variant creation
     * AND updates variant requests with real UUIDs for newly created attribute values
     *
     * @param attributeSelections List of attribute selections from request
     * @param variantRequests List of variant requests to validate against
     */
    private void processAttributeSelections(List<AttributeSelectionRequest> attributeSelections,
                                            List<ProductVariantRequest> variantRequests) {
        log.info("Processing {} attribute selections for product creation", attributeSelections.size());

        // Map to track: clientUuid/tempValue -> actual UUID from database
        java.util.Map<String, String> attributeValueUuidMapping = new java.util.HashMap<>();

        for (AttributeSelectionRequest selection : attributeSelections) {
            ProductAttribute attribute;

            // Handle new attribute creation
            if (Boolean.TRUE.equals(selection.getIsNew())) {
                if (selection.getAttributeName() == null || selection.getAttributeName().trim().isEmpty()) {
                    throw new BadRequestException("Attribute name is required for new attributes");
                }

                // Check if attribute already exists
                if (attributeRepository.existsByName(selection.getAttributeName())) {
                    throw new BadRequestException("Attribute with name '" + selection.getAttributeName() +
                            "' already exists. Please use existing attribute instead.");
                }

                // Create new attribute
                attribute = ProductAttribute.builder()
                        .name(selection.getAttributeName().trim())
                        .isActive(true)
                        .build();

                attribute = attributeRepository.save(attribute);
                log.info("Created new attribute: {} with UUID: {}", attribute.getName(), attribute.getUuid());

            } else {
                // Use existing attribute
                if (selection.getAttributeUuid() == null || selection.getAttributeUuid().trim().isEmpty()) {
                    throw new BadRequestException("Attribute UUID is required for existing attributes");
                }

                attribute = attributeRepository.findByUuid(selection.getAttributeUuid())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Attribute not found with UUID: " + selection.getAttributeUuid()));

                if (!attribute.getIsActive()) {
                    throw new BadRequestException("Cannot use inactive attribute: " + attribute.getName());
                }
            }

            // Process attribute values
            for (AttributeValueSelectionRequest valueSelection : selection.getValues()) {
                ProductAttributeValue attributeValue;

                // Handle new attribute value creation
                if (Boolean.TRUE.equals(valueSelection.getIsNew())) {
                    if (valueSelection.getValue() == null || valueSelection.getValue().trim().isEmpty()) {
                        throw new BadRequestException("Value is required for new attribute values");
                    }

                    String trimmedValue = valueSelection.getValue().trim();

                    // Check if value already exists for this attribute
                    java.util.Optional<ProductAttributeValue> existingValue =
                            attributeValueRepository.findByAttributeIdAndValue(attribute.getId(), trimmedValue);

                    if (existingValue.isPresent()) {
                        // Value already exists, use it
                        attributeValue = existingValue.get();
                        log.info("Attribute value '{}' already exists for attribute '{}', using existing value",
                                trimmedValue, attribute.getName());
                    } else {
                        // Create new attribute value
                        attributeValue = ProductAttributeValue.builder()
                                .attributeId(attribute.getId())
                                .value(trimmedValue)
                                .isActive(true)
                                .build();

                        attributeValue = attributeValueRepository.save(attributeValue);
                        log.info("Created new attribute value: {} for attribute: {}",
                                attributeValue.getValue(), attribute.getName());
                    }

                    // Map the temp UUID (if provided) or value string to the real UUID
                    // This allows variants to reference the newly created attribute value
                    if (valueSelection.getAttributeValueUuid() != null && !valueSelection.getAttributeValueUuid().isEmpty()) {
                        // Client provided a temp UUID, map it to the real one
                        attributeValueUuidMapping.put(valueSelection.getAttributeValueUuid(), attributeValue.getUuid());
                        log.info("Mapped temp UUID {} to real UUID {} for value '{}'",
                                valueSelection.getAttributeValueUuid(), attributeValue.getUuid(), trimmedValue);
                    }
                    // Also map by value for lookup
                    String mapKey = attribute.getId() + ":" + trimmedValue;
                    attributeValueUuidMapping.put(mapKey, attributeValue.getUuid());

                } else {
                    // Use existing attribute value
                    if (valueSelection.getAttributeValueUuid() == null ||
                            valueSelection.getAttributeValueUuid().trim().isEmpty()) {
                        throw new BadRequestException("Attribute value UUID is required for existing values");
                    }

                    attributeValue = attributeValueRepository.findByUuid(valueSelection.getAttributeValueUuid())
                            .orElseThrow(() -> new ResourceNotFoundException(
                                    "Attribute value not found with UUID: " + valueSelection.getAttributeValueUuid()));

                    // Verify this value belongs to the correct attribute
                    if (!attributeValue.getAttributeId().equals(attribute.getId())) {
                        throw new BadRequestException("Attribute value '" + attributeValue.getValue() +
                                "' does not belong to attribute '" + attribute.getName() + "'");
                    }

                    if (!attributeValue.getIsActive()) {
                        throw new BadRequestException("Cannot use inactive attribute value: " + attributeValue.getValue());
                    }
                }
            }

            log.info("Processed values for attribute: {}", attribute.getName());
        }

        // Update variant requests with real UUIDs for newly created attribute values
        if (!attributeValueUuidMapping.isEmpty() && variantRequests != null) {
            for (ProductVariantRequest variantRequest : variantRequests) {
                if (variantRequest.getAttributeValueUuids() != null && !variantRequest.getAttributeValueUuids().isEmpty()) {
                    List<String> updatedUuids = new java.util.ArrayList<>();
                    for (String uuid : variantRequest.getAttributeValueUuids()) {
                        // Check if this UUID was mapped (it was a temp UUID for a new attribute value)
                        String realUuid = attributeValueUuidMapping.getOrDefault(uuid, uuid);
                        updatedUuids.add(realUuid);
                        if (!uuid.equals(realUuid)) {
                            log.info("Updated variant SKU {} attribute value UUID from {} to {}",
                                    variantRequest.getSku(), uuid, realUuid);
                        }
                    }
                    variantRequest.setAttributeValueUuids(updatedUuids);
                }
            }
        }

        // Validate that all variants have valid attribute value UUIDs
        validateVariantAttributeValues(variantRequests);

        log.info("Successfully processed all attribute selections and updated variant attribute value UUIDs");
    }

    /**
     * Validate that variant attribute value UUIDs exist in the database
     * This should be called after processAttributeSelections
     */
    private void validateVariantAttributeValues(List<ProductVariantRequest> variantRequests) {
        for (ProductVariantRequest variantRequest : variantRequests) {
            if (variantRequest.getAttributeValueUuids() == null ||
                    variantRequest.getAttributeValueUuids().isEmpty()) {
                log.warn("Variant with SKU {} has no attribute values", variantRequest.getSku());
                continue;
            }

            for (String attributeValueUuid : variantRequest.getAttributeValueUuids()) {
                if (!attributeValueRepository.findByUuid(attributeValueUuid).isPresent()) {
                    throw new BadRequestException("Attribute value not found with UUID: " + attributeValueUuid +
                            " for variant SKU: " + variantRequest.getSku());
                }
            }
        }
    }

    private void validateSlug(String slug, String title) {
        if (slug.contains(" ")) {
            throw new BadRequestException("Slug cannot contain spaces. Use hyphens instead.");
        }

        if (!slug.matches("^[a-z0-9-]+$")) {
            throw new BadRequestException("Slug must contain only lowercase letters, numbers, and hyphens");
        }

        String generatedSlug = generateSlugFromTitle(title);
        if (!slug.equals(generatedSlug)) {
            log.warn("Provided slug '{}' differs from auto-generated slug '{}' for title '{}'", slug, generatedSlug, title);
        }
    }

    private String generateSlugFromTitle(String title) {
        return title.toLowerCase()
                .trim()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }

    private void saveProductGallery(Long productId, List<ProductGalleryRequest> galleryRequests) {
        for (ProductGalleryRequest galleryRequest : galleryRequests) {
            ProductGallery gallery = ProductGallery.builder()
                    .productId(productId)
                    .mediaName(toFullUrl(galleryRequest.getMediaName()))
                    .sortOrder(galleryRequest.getSortOrder())
                    .altText(galleryRequest.getAltText())
                    .mimeType(galleryRequest.getMimeType())
                    .mediaType(ProductGallery.MediaType.IMAGE)
                    .isActive(true)
                    .build();
            galleryRepository.save(gallery);
        }
        log.info("Saved {} gallery images for product ID: {}", galleryRequests.size(), productId);
    }

    /**
     * Save product gallery images and move them from temp folder to final product folder
     */
    private void saveProductGalleryWithImageMove(Long productId, String productUuid, List<ProductGalleryRequest> galleryRequests) {
        for (ProductGalleryRequest galleryRequest : galleryRequests) {
            String finalMediaPath = galleryRequest.getMediaName();

            // Move image from temp to final folder if it's a temp image
            String tempUuid = imageStorageService.extractTempUuidFromPath(galleryRequest.getMediaName());
            if (tempUuid != null && imageStorageService.isTempImage(galleryRequest.getMediaName())) {
                finalMediaPath = imageStorageService.moveProductImageToFinal(
                        tempUuid,
                        productUuid,
                        galleryRequest.getMediaName()
                );
                log.info("Moved gallery image from temp to final: {}", finalMediaPath);
            }

            ProductGallery gallery = ProductGallery.builder()
                    .productId(productId)
                    .mediaName(toFullUrl(finalMediaPath))
                    .sortOrder(galleryRequest.getSortOrder())
                    .altText(galleryRequest.getAltText())
                    .mimeType(galleryRequest.getMimeType())
                    .mediaType(ProductGallery.MediaType.IMAGE)
                    .isActive(true)
                    .build();
            galleryRepository.save(gallery);
        }
        log.info("Saved and moved {} gallery images for product ID: {}", galleryRequests.size(), productId);
    }

    private void saveProductMeta(Long productId, ProductMetaRequest metaRequest) {
        ProductMeta meta = ProductMeta.builder()
                .productId(productId)
                .metaTitle(metaRequest.getMetaTitle())
                .metaDescription(metaRequest.getMetaDescription())
                .canonicalUrl(metaRequest.getCanonicalUrl())
                .metaKeywords(metaRequest.getMetaKeywords())
                .ogTitle(metaRequest.getOgTitle())
                .ogDescription(metaRequest.getOgDescription())
                .ogImage(metaRequest.getOgImage())
                .twitterCard(metaRequest.getTwitterCard())
                .jsonLd(metaRequest.getJsonLd())
                .robotIndex(metaRequest.getRobotIndex())
                .isActive(true)
                .build();
        metaRepository.save(meta);
        log.info("Saved product meta for product ID: {}", productId);
    }

    private void saveProductDimension(Long productId, ProductDimensionRequest dimensionRequest) {
        ProductDimension dimension = ProductDimension.builder()
                .productId(productId)
                .weightKg(dimensionRequest.getWeightKg())
                .lengthCm(dimensionRequest.getLengthCm())
                .widthCm(dimensionRequest.getWidthCm())
                .heightCm(dimensionRequest.getHeightCm())
                .isActive(true)
                .build();
        dimensionRepository.save(dimension);
        log.info("Saved product dimension for product ID: {}", productId);
    }

    private void saveProductTags(Long productId, List<String> tagUuids) {
        for (String tagUuid : tagUuids) {
            ProductTag tag = tagRepository.findByUuid(tagUuid)
                    .orElseThrow(() -> new ResourceNotFoundException("Tag not found with UUID: " + tagUuid));

            if (!tagMapRepository.existsByProductIdAndTagId(productId, tag.getId())) {
                ProductTagMap tagMap = ProductTagMap.builder()
                        .productId(productId)
                        .tagId(tag.getId())
                        .build();
                tagMapRepository.save(tagMap);
            }
        }
        log.info("Saved {} tags for product ID: {}", tagUuids.size(), productId);
    }


    private Long saveProductVariants(Product product, List<ProductVariantRequest> variantRequests, Product.ProductType productType, BigDecimal resellerPrice) {
        Long defaultVariantId = null;
        boolean hasDefaultVariant = false;

        // For SIMPLE products without explicit variants, auto-generate one from product details
        if (productType == Product.ProductType.SIMPLE && (variantRequests == null || variantRequests.isEmpty())) {
            log.info("Auto-generating variant for SIMPLE product ID: {} with SKU: {}", product.getId(), product.getSku());

            // Get dimensions from product if they exist
            BigDecimal weightKg = null;
            BigDecimal lengthCm = null;
            BigDecimal widthCm = null;
            BigDecimal heightCm = null;

            java.util.Optional<ProductDimension> dimensionOpt = dimensionRepository.findByProductId(product.getId());
            if (dimensionOpt.isPresent()) {
                ProductDimension dim = dimensionOpt.get();
                weightKg = dim.getWeightKg();
                lengthCm = dim.getLengthCm();
                widthCm = dim.getWidthCm();
                heightCm = dim.getHeightCm();
            }

            //validate reseller price greater than zero
            if(resellerPrice == null || resellerPrice.compareTo(BigDecimal.ZERO) <= 0){
                resellerPrice = null;
            }



            // Create a single variant from product's main details
            ProductVariant variant = ProductVariant.builder()
                    .sku(product.getSku() + "-DEFAULT") // Append suffix to avoid SKU conflict
                    .productId(product.getId())
                    .attributeSummary("Default") // Default summary for simple products
                    .weightKg(weightKg)
                    .lengthCm(lengthCm)
                    .widthCm(widthCm)
                    .heightCm(heightCm)
                    .thumbnailUrl(product.getThumbnailUrl())
                    .showcasePrice(product.getShowcasePrice())
                    .salePrice(product.getSalePrice())
                    .saleStart(product.getSaleStart())
                    .saleEnd(product.getSaleEnd())
                    .resellerPrice(resellerPrice)
                    .inventoryStatus(product.getInventoryStatus() == Product.InventoryStatus.IN_STOCK ? ProductVariant.InventoryStatus.IN_STOCK : ProductVariant.InventoryStatus.OUT_OF_STOCK)
                    .wholesalePrice(product.getWholesalePrice())
                    .wholesaleMinQty(product.getWholesaleMinQty())
                    .productionCost(product.getProductionCost())
                    .isActive(true)
                    .build();

            variant = variantRepository.save(variant);
            log.info("Auto-generated variant with ID: {} and SKU: {} for SIMPLE product", variant.getId(), variant.getSku());


            log.info("Skipping VariantCreatedEvent for auto-generated SIMPLE variant (stock already initialized via ProductCreatedEvent)");

            return variant.getId();
        }

        // Process explicit variants (for VARIABLE products or SIMPLE with explicit variant)
        for (ProductVariantRequest variantRequest : variantRequests) {
            if (variantRepository.existsActiveBySku(variantRequest.getSku())) {
                throw new BadRequestException("Variant with SKU '" + variantRequest.getSku() + "' already exists");
            }

            ProductVariant variant = ProductVariant.builder()
                    .sku(variantRequest.getSku())
                    .productId(product.getId())
                    .attributeSummary(variantRequest.getAttributeSummary())
                    .weightKg(variantRequest.getWeightKg())
                    .lengthCm(variantRequest.getLengthCm())
                    .widthCm(variantRequest.getWidthCm())
                    .heightCm(variantRequest.getHeightCm())
                    .thumbnailUrl(toFullUrl(variantRequest.getThumbnailImageName()))
                    .showcasePrice(variantRequest.getShowcasePrice())
                    .salePrice(variantRequest.getSalePrice())
                    .saleStart(variantRequest.getSaleStart())
                    .saleEnd(variantRequest.getSaleEnd())
                    .resellerPrice(variantRequest.getResellerPrice())
                    .inventoryStatus(variantRequest.getInventoryStatus().equalsIgnoreCase("IN_STOCK") ? ProductVariant.InventoryStatus.IN_STOCK : ProductVariant.InventoryStatus.OUT_OF_STOCK)
                    .wholesalePrice(variantRequest.getWholesalePrice())
                    .wholesaleMinQty(variantRequest.getWholesaleMinQty())
                    .productionCost(variantRequest.getProductionCost())
                    .isActive(true)
                    .build();

            variant = variantRepository.save(variant);
            log.info("Created variant with ID: {} and SKU: {}", variant.getId(), variant.getSku());

            // Publish Variant Created Event for Inventory Module
            publishVariantCreatedEvent(variant, variantRequest);

            if ((productType == Product.ProductType.SIMPLE && !hasDefaultVariant) ||
                    (Boolean.TRUE.equals(variantRequest.getIsDefault()) && !hasDefaultVariant)) {
                defaultVariantId = variant.getId();
                hasDefaultVariant = true;
            }

            if (variantRequest.getAttributeValueUuids() != null && !variantRequest.getAttributeValueUuids().isEmpty()) {
                saveVariantAttributeValues(variant.getId(), variantRequest.getAttributeValueUuids());
            }
        }

        if (defaultVariantId == null && !variantRequests.isEmpty()) {
            defaultVariantId = variantRepository.findByProductId(product.getId()).get(0).getId();
        }

        return defaultVariantId;
    }

    private void saveVariantAttributeValues(Long variantId, List<String> attributeValueUuids) {
        for (String attributeValueUuid : attributeValueUuids) {
            ProductAttributeValue attributeValue = attributeValueRepository.findByUuid(attributeValueUuid)
                    .orElseThrow(() -> new ResourceNotFoundException("Attribute value not found with UUID: " + attributeValueUuid));

            VariantAttributeValue variantAttributeValue = VariantAttributeValue.builder()
                    .variantId(variantId)
                    .attributeValueId(attributeValue.getId())
                    .isActive(true)
                    .build();
            variantAttributeValueRepository.save(variantAttributeValue);
        }
        log.info("Saved {} attribute values for variant ID: {}", attributeValueUuids.size(), variantId);
    }

    private void savePriceHistory(Long productId, Long variantId, BigDecimal oldPrice, BigDecimal newPrice) {
        ProductPriceHistory priceHistory = ProductPriceHistory.builder()
                .productId(productId)
                .variantId(variantId)
                .oldPrice(oldPrice)
                .newPrice(newPrice)
                .changedBy("SYSTEM")
                .changedAt(LocalDateTime.now())
                .isActive(true)
                .build();
        priceHistoryRepository.save(priceHistory);
    }

    private ProductResponse mapToProductResponse(Product product) {
        CategoryResponse categoryResponse = null;
        if (product.getCategoryId() != null) {
            ProductCategory category = categoryRepository.findById(product.getCategoryId()).orElse(null);
            if (category != null) {
                //get parent category if exists
                ProductCategory parentCategory = null;
                if (category.getParentId() != null) {
                    parentCategory = categoryRepository.findById(category.getParentId()).orElse(null);
                }
                //Check category has any sub-categories
                List<ProductCategory> allByParentId = categoryRepository.findAllByParentId(category.getId());

                categoryResponse = CategoryResponse.builder()
                        .uuid(category.getUuid())
                        .name(category.getName())
                        .slug(category.getSlug())
                        .parentCategoryName(parentCategory == null ? null : parentCategory.getName())
                        .parentCategoryUuid(parentCategory == null ? null : parentCategory.getUuid())
                        .isActive(category.getIsActive())
                        .dateCreated(category.getDateCreated())
                        .dateUpdated(category.getDateUpdated())
                        .subCategories(allByParentId.stream().map(ProductCategory::getUuid).collect(Collectors.toList()))
                        .build();
            }
        }

        List<ProductGalleryResponse> galleryResponses = galleryRepository.findByProductIdAndIsActiveTrueOrderBySortOrderAsc(product.getId())
                .stream()
                .map(g -> ProductGalleryResponse.builder()
                        .uuid(g.getUuid())
                        .mediaUrl(g.getMediaName())
                        .sortOrder(g.getSortOrder())
                        .altText(g.getAltText())
                        .mimeType(g.getMimeType())
                        .build())
                .collect(Collectors.toList());

        ProductMetaResponse metaResponse = metaRepository.findByProductId(product.getId())
                .map(m -> ProductMetaResponse.builder()
                        .uuid(m.getUuid())
                        .metaTitle(m.getMetaTitle())
                        .metaDescription(m.getMetaDescription())
                        .canonicalUrl(m.getCanonicalUrl())
                        .metaKeywords(m.getMetaKeywords())
                        .ogTitle(m.getOgTitle())
                        .ogDescription(m.getOgDescription())
                        .ogImage(m.getOgImage())
                        .twitterCard(m.getTwitterCard())
                        .jsonLd(m.getJsonLd())
                        .robotIndex(m.getRobotIndex())
                        .build())
                .orElse(null);

        ProductDimensionResponse dimensionResponse = dimensionRepository.findByProductId(product.getId())
                .map(d -> ProductDimensionResponse.builder()
                        .uuid(d.getUuid())
                        .weightKg(d.getWeightKg() != null ? d.getWeightKg().toString() : null)
                        .lengthCm(d.getLengthCm() != null ? d.getLengthCm().toString() : null)
                        .widthCm(d.getWidthCm() != null ? d.getWidthCm().toString() : null)
                        .heightCm(d.getHeightCm() != null ? d.getHeightCm().toString() : null)
                        .build())
                .orElse(null);

        List<ProductTagResponse> tagResponses = tagMapRepository.findByProductId(product.getId())
                .stream()
                .map(tm -> {
                    ProductTag tag = tagRepository.findById(tm.getTagId()).orElse(null);
                    if (tag != null) {
                        return ProductTagResponse.builder()
                                .uuid(tag.getUuid())
                                .name(tag.getName())
                                .slug(tag.getSlug())
                                .description(tag.getDescription())
                                .build();
                    }
                    return null;
                })
                .filter(t -> t != null)
                .collect(Collectors.toList());

        List<ProductVariantResponse> variantResponses = variantRepository.findByProductId(product.getId())
                .stream()
                .map(variant -> mapToVariantResponse(variant, product.getId()))
                .collect(Collectors.toList());

        ProductVariantResponse defaultVariantResponse = null;
        if (product.getDefaultVariantId() != null) {
            defaultVariantResponse = variantRepository.findById(product.getDefaultVariantId())
                    .map(variant -> mapToVariantResponse(variant, product.getId()))
                    .orElse(null);
        }

        return ProductResponse.builder()
                .uuid(product.getUuid())
                .sku(product.getSku())
                .title(product.getTitle())
                .slug(product.getSlug())
                .shortDescription(product.getShortDescription())
                .description(product.getDescription())
                .category(categoryResponse)
                .thumbnailUrl(product.getThumbnailUrl() != null ? product.getThumbnailUrl() : null)
                .showcasePrice(product.getShowcasePrice() != null ? product.getShowcasePrice().toString() : null)
                .salePrice(product.getSalePrice() != null ? product.getSalePrice().toString() : null)
                .saleStart(product.getSaleStart())
                .saleEnd(product.getSaleEnd())
                .productType(product.getProductType().name())
                .isResellerProduct(product.getIsResellerProduct())
                .featured(product.getFeatured())
                .visibility(product.getVisibility().name())
                .soldIndividually(product.getSoldIndividually())
                .pricingMode(product.getPricingMode().name())
                .wholesalePrice(product.getWholesalePrice() != null ? product.getWholesalePrice().toString() : null)
                .wholesaleMinQty(product.getWholesaleMinQty())
                .productionCost(product.getProductionCost() != null ? product.getProductionCost().toString() : null)
                .isPublish(product.getIsPublish())
                .isActive(product.getIsActive())
                .tags(tagResponses)
                .galleryImages(galleryResponses)
                .meta(metaResponse)
                .dimension(dimensionResponse)
                .variants(variantResponses)
                .defaultVariant(defaultVariantResponse)
                .dateCreated(product.getDateCreated())
                .dateUpdated(product.getDateUpdated())
                .build();
    }

    private ProductVariantResponse mapToVariantResponse(ProductVariant variant, Long productId) {
        log.debug("mapToVariantResponse: variantId={}, productId={}, variantSku={}",
            variant.getId(), productId, variant.getSku());

        List<VariantGalleryResponse> galleryResponses = variantGalleryRepository.findByVariantIdOrderBySortOrder(variant.getId())
                .stream()
                .map(g -> VariantGalleryResponse.builder()
                        .uuid(g.getUuid())
                        .mediaUrl(g.getMediaName())
                        .sortOrder(g.getSortOrder())
                        .altText(g.getAltText())
                        .mimeType(g.getMimeType())
                        .build())
                .collect(Collectors.toList());

        List<AttributeValueResponse> attributeValueResponses = variantAttributeValueRepository.findByVariantId(variant.getId())
                .stream()
                .map(vav -> {
                    ProductAttributeValue av = attributeValueRepository.findById(vav.getAttributeValueId()).orElse(null);
                    if (av != null) {
                        return AttributeValueResponse.builder()
                                .uuid(av.getUuid())
                                .value(av.getValue())
                                .isActive(av.getIsActive())
                                .build();
                    }
                    return null;
                })
                .filter(av -> av != null)
                .collect(Collectors.toList());

        // STEP 1: Fetch stock quantity from inventory module via RabbitMQ
        Integer stockQuantity = null;
        try {
            stockQuantity = inventoryStockService.getVariantStockQuantity(productId, variant.getId());
            log.debug("Stock received: stockQuantity={}", stockQuantity);
        } catch (Exception e) {
            // Inventory service may be unavailable or throw; do not let product fetch fail because of inventory lookup.
            log.warn("Could not fetch stock for productId={} variantId={}. Defaulting stockQuantity to 0. Error: {}",
                    productId, variant.getId(), e.getMessage());
            log.debug("Inventory fetch error", e);
            stockQuantity = 0;
        }

        return ProductVariantResponse.builder()
                .uuid(variant.getUuid())
                .sku(variant.getSku())
                .attributeSummary(variant.getAttributeSummary())
                .weightKg(variant.getWeightKg() != null ? variant.getWeightKg().toString() : null)
                .lengthCm(variant.getLengthCm() != null ? variant.getLengthCm().toString() : null)
                .widthCm(variant.getWidthCm() != null ? variant.getWidthCm().toString() : null)
                .heightCm(variant.getHeightCm() != null ? variant.getHeightCm().toString() : null)
                .thumbnailUrl(variant.getThumbnailUrl() != null ? variant.getThumbnailUrl() : null)
                .showcasePrice(variant.getShowcasePrice() != null ? variant.getShowcasePrice().toString() : null)
                .salePrice(variant.getSalePrice() != null ? variant.getSalePrice().toString() : null)
                .saleStart(variant.getSaleStart())
                .saleEnd(variant.getSaleEnd())
                .resellerPrice(variant.getResellerPrice() != null ? variant.getResellerPrice().toString() : null)
                .wholesalePrice(variant.getWholesalePrice() != null ? variant.getWholesalePrice().toString() : null)
                .wholesaleMinQty(variant.getWholesaleMinQty())
                .productionCost(variant.getProductionCost() != null ? variant.getProductionCost().toString() : null)
                .isActive(variant.getIsActive())
                .stockQuantity(stockQuantity)
                .attributeValues(attributeValueResponses)
                .galleryImages(galleryResponses)
                .dateCreated(variant.getDateCreated())
                .dateUpdated(variant.getDateUpdated())
                .build();
    }

    private PageResponse<ProductResponse> mapToPageResponse(Page<Product> productPage) {
        List<ProductResponse> content = productPage.getContent().stream()
                .map(this::mapToProductResponse)
                .collect(Collectors.toList());

        return PageResponse.<ProductResponse>builder()
                .content(content)
                .pageNumber(productPage.getNumber())
                .pageSize(productPage.getSize())
                .totalElements(productPage.getTotalElements())
                .totalPages(productPage.getTotalPages())
                .last(productPage.isLast())
                .build();
    }

    /**
     * Map Product entity to ProductCardResponse with rating information
     * This is used for website homepage/listing display
     */
    private ProductCardResponse mapToProductCardResponse(Product product) {
        // Get rating aggregate data
        ProductRatingAggregate ratingAggregate = ratingAggregateRepository
                .findByProductId(product.getId())
                .orElse(null);

        Integer reviewCount = ratingAggregate != null ? ratingAggregate.getReviewCount() : 0;
        BigDecimal averageRating = ratingAggregate != null ? ratingAggregate.getRatingAverage() : BigDecimal.ZERO;

        // Calculate discount percentage
        Integer discountPercentage = null;
        if (product.getSalePrice() != null && product.getShowcasePrice() != null
                && product.getSalePrice().compareTo(BigDecimal.ZERO) > 0
                && product.getShowcasePrice().compareTo(product.getSalePrice()) > 0) {
            // Calculate: ((showcasePrice - salePrice) / showcasePrice) * 100
            BigDecimal discount = product.getShowcasePrice().subtract(product.getSalePrice());
            BigDecimal discountRatio = discount.divide(product.getShowcasePrice(), 4, java.math.RoundingMode.HALF_UP);
            discountPercentage = discountRatio.multiply(new BigDecimal("100")).intValue();
        }

        return ProductCardResponse.builder()
                .uuid(product.getUuid())
                .title(product.getTitle())
                .slug(product.getSlug())
                .thumbnailUrl(product.getThumbnailUrl())
                .showcasePrice(product.getShowcasePrice())
                .salePrice(product.getSalePrice())
                .discountPercentage(discountPercentage)
                .reviewCount(reviewCount)
                .averageRating(averageRating)
                .featured(product.getFeatured())
                .isPublish(product.getIsPublish())
                .build();
    }

    private ProductCardResponse mapToProductCardResponse(Product product, ProductRatingAggregate ratingAggregate) {
        Integer reviewCount = ratingAggregate != null ? ratingAggregate.getReviewCount() : 0;
        BigDecimal averageRating = ratingAggregate != null ? ratingAggregate.getRatingAverage() : BigDecimal.ZERO;

        // Calculate discount percentage
        Integer discountPercentage = null;
        if (product.getSalePrice() != null && product.getShowcasePrice() != null
                && product.getSalePrice().compareTo(BigDecimal.ZERO) > 0
                && product.getShowcasePrice().compareTo(product.getSalePrice()) > 0) {
            // Calculate: ((showcasePrice - salePrice) / showcasePrice) * 100
            BigDecimal discount = product.getShowcasePrice().subtract(product.getSalePrice());
            BigDecimal discountRatio = discount.divide(product.getShowcasePrice(), 4, java.math.RoundingMode.HALF_UP);
            discountPercentage = discountRatio.multiply(new BigDecimal("100")).intValue();
        }

        return ProductCardResponse.builder()
                .uuid(product.getUuid())
                .title(product.getTitle())
                .slug(product.getSlug())
                .thumbnailUrl(product.getThumbnailUrl())
                .showcasePrice(product.getShowcasePrice())
                .salePrice(product.getSalePrice())
                .discountPercentage(discountPercentage)
                .reviewCount(reviewCount)
                .averageRating(averageRating)
                .featured(product.getFeatured())
                .isPublish(product.getIsPublish())
                .build();
    }

    /**
     * Map Product entity to ProductFilterResponse with colors, sizes, ratings, and stock info
     */
    private ProductFilterResponse mapToProductFilterResponse(Product product, ProductRatingAggregate ratingAggregate, List<ProductVariant> variants) {
        // Get category name and sub-category (if applicable)
        String categoryName = null;
        String subCategoryName = null;
        if (product.getCategoryId() != null) {
            ProductCategory category = categoryRepository.findById(product.getCategoryId()).orElse(null);
            if (category != null) {
                if (category.getParentId() != null) {
                    ProductCategory parent = categoryRepository.findById(category.getParentId()).orElse(null);
                    if (parent != null) {
                        categoryName = parent.getName();
                        subCategoryName = category.getName();
                    } else {
                        categoryName = category.getName();
                        subCategoryName = null;
                    }
                } else {
                    categoryName = category.getName();
                    subCategoryName = null;
                }
            }
        }

        // Get default variant for pricing
        ProductVariant defaultVariant = null;
        if (product.getDefaultVariantId() != null && variants != null && !variants.isEmpty()) {
            defaultVariant = variants.stream()
                    .filter(v -> v.getId().equals(product.getDefaultVariantId()))
                    .findFirst()
                    .orElse(null);
        }

        // If no default variant found, use first variant
        if (defaultVariant == null && variants != null && !variants.isEmpty()) {
            defaultVariant = variants.get(0);
        }

        // Get prices from variant (if exists), otherwise fall back to product prices
        BigDecimal salePrice;
        BigDecimal showcasePrice;

        if (defaultVariant != null) {
            // Use variant prices
            salePrice = defaultVariant.getSalePrice();
            showcasePrice = defaultVariant.getShowcasePrice();
        } else {
            // Fall back to product prices
            salePrice = product.getSalePrice();
            showcasePrice = product.getShowcasePrice();
        }

        // Calculate discount percentage using variant or product prices
        Integer discountPercentage = null;
        if (salePrice != null && showcasePrice != null
                && salePrice.compareTo(BigDecimal.ZERO) > 0
                && showcasePrice.compareTo(salePrice) > 0) {
            BigDecimal discount = showcasePrice.subtract(salePrice);
            BigDecimal discountRatio = discount.divide(showcasePrice, 4, java.math.RoundingMode.HALF_UP);
            discountPercentage = discountRatio.multiply(new BigDecimal("100")).intValue();
        }

        // Get rating and review count
        Double rating = ratingAggregate != null ? ratingAggregate.getRatingAverage().doubleValue() : 0.0;
        Long reviews = ratingAggregate != null ? ratingAggregate.getReviewCount().longValue() : 0L;

        // Defensive: ensure variants is not null
        if (variants == null) {
            variants = java.util.List.of();
        }

        // Extract colors and sizes from variants
        List<String> colors = new java.util.ArrayList<>();
        List<String> sizes = new java.util.ArrayList<>();

        for (ProductVariant variant : variants) {
            // Get attribute values for this variant
            List<VariantAttributeValue> variantAttributeValues = variantAttributeValueRepository.findByVariantId(variant.getId());

            for (VariantAttributeValue vav : variantAttributeValues) {
                ProductAttributeValue attributeValue = attributeValueRepository.findById(vav.getAttributeValueId()).orElse(null);
                if (attributeValue != null) {
                    ProductAttribute attribute = attributeRepository.findById(attributeValue.getAttributeId()).orElse(null);
                    if (attribute != null) {
                        String attributeName = attribute.getName().toLowerCase();
                        String value = attributeValue.getValue();

                        // Categorize by attribute name
                        if (attributeName.contains("color") || attributeName.contains("colour")) {
                            if (!colors.contains(value)) {
                                colors.add(value);
                            }
                        } else if (attributeName.contains("size")) {
                            if (!sizes.contains(value)) {
                                sizes.add(value);
                            }
                        }
                    }
                }
            }
        }

        // Check stock availability
        Boolean inStock = product.getInventoryStatus() == Product.InventoryStatus.IN_STOCK;

        return ProductFilterResponse.builder()
                .uuid(product.getUuid())
                .name(product.getTitle())
                .slug(product.getSlug())
                .category(categoryName)
                .subCategory(subCategoryName)
                .salePrice(salePrice)
                .showcasePrice(showcasePrice)
                .discount(discountPercentage)
                .rating(rating)
                .reviews(reviews)
                .colors(colors)
                .sizes(sizes)
                .inStock(inStock)
                .featured(product.getFeatured())
                .image(product.getThumbnailUrl())
                .description(product.getShortDescription())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Map<String, String>> getProductAttributesMap(String productUuid) {
        log.info("Getting product attributes map for product UUID: {}", productUuid);

        // Step 1: Find product by UUID
        Product product = productRepository.findByUuid(productUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with UUID: " + productUuid));

        // Step 2: Find all ACTIVE variants for this product using product_id
        List<ProductVariant> variants = variantRepository.findByProductIdAndIsActive(product.getId(), true);
        log.debug("Found {} active variants for product ID: {}", variants.size(), product.getId());

        // Map to store results: Key = variant UUID, Value = Map of attribute name to joined string value
        Map<String, Map<String, String>> resultMap = new java.util.LinkedHashMap<>();

        // Step 3: Process each variant
        for (ProductVariant variant : variants) {
            log.debug("Processing variant UUID: {}, ID: {}, SKU: {}, AttributeSummary: {}",
                    variant.getUuid(), variant.getId(), variant.getSku(), variant.getAttributeSummary());

            // Step 4: Find attribute_value_ids for this variant using variant_id
            List<VariantAttributeValue> variantAttributeValues = variantAttributeValueRepository.findByVariantId(variant.getId());

            // Map to store attribute information for this variant (attribute name -> list of values)
            Map<String, java.util.List<String>> tempAttributeMap = new java.util.LinkedHashMap<>();

            // Step 5: For each attribute value, find the product attribute
            for (VariantAttributeValue vav : variantAttributeValues) {
                // Get the attribute value using attribute_value_id
                ProductAttributeValue attributeValue = attributeValueRepository.findById(vav.getAttributeValueId())
                        .orElse(null);

                if (attributeValue != null) {
                    log.debug("Found ProductAttributeValue - ID: {}, Value: {}, AttributeID: {}",
                            attributeValue.getId(), attributeValue.getValue(), attributeValue.getAttributeId());

                    // Get the product attribute that matches with attribute_value
                    ProductAttribute attribute = attributeRepository.findById(attributeValue.getAttributeId())
                            .orElse(null);

                    if (attribute != null) {
                        log.debug("Found ProductAttribute - ID: {}, Name: {}", attribute.getId(), attribute.getName());

                        // Add to temp map: accumulate multiple values for the same attribute name
                        tempAttributeMap.computeIfAbsent(attribute.getName(), k -> new java.util.ArrayList<>())
                                .add(attributeValue.getValue());
                    }
                }
            }

            log.debug("Variant {} has {} attributes (raw list map): {}", variant.getUuid(), tempAttributeMap.size(), tempAttributeMap);

            // Fallback: If no attributes found in junction tables but attributeSummary exists, parse it
            if (tempAttributeMap.isEmpty() && variant.getAttributeSummary() != null && !variant.getAttributeSummary().isEmpty()) {
                log.info("No attributes found in junction tables for variant {}, using attributeSummary: {}",
                        variant.getUuid(), variant.getAttributeSummary());

                // Parse attributeSummary (format: "Color: Black, Size: M" or "Color: Black, Red, Size: M, S")
                String[] attributes = variant.getAttributeSummary().split(",");
                String currentAttributeName = null;

                for (String attr : attributes) {
                    String trimmedAttr = attr.trim();
                    if (!trimmedAttr.isEmpty()) {
                        if (trimmedAttr.contains(":")) {
                            // This is a new attribute (e.g., "Color: Black" or "Size: M")
                            String[] parts = trimmedAttr.split(":", 2);
                            if (parts.length == 2) {
                                currentAttributeName = parts[0].trim();
                                String attributeValue = parts[1].trim();
                                tempAttributeMap.computeIfAbsent(currentAttributeName, k -> new java.util.ArrayList<>())
                                        .add(attributeValue);
                            }
                        } else if (currentAttributeName != null) {
                            // This is a continuation value (e.g., "Red" after "Color: Black")
                            tempAttributeMap.computeIfAbsent(currentAttributeName, k -> new java.util.ArrayList<>())
                                    .add(trimmedAttr);
                        }
                    }
                }
                log.info("Parsed {} attributes from attributeSummary", tempAttributeMap.size());
            }

            // Convert tempAttributeMap to final map: use single value per attribute per variant
            Map<String, String> attributeMap = new LinkedHashMap<>();
            for (Map.Entry<String,List<String>> entry : tempAttributeMap.entrySet()) {
                // If there are duplicate values from multiple sources, deduplicate and take the first
                LinkedHashSet<String> uniqueValues = new LinkedHashSet<>(entry.getValue());

                if (!uniqueValues.isEmpty()) {
                    String singleValue = uniqueValues.iterator().next();
                    attributeMap.put(entry.getKey(), singleValue);
                }
            }

            // Step 6: Add to result map with variant UUID as key
            resultMap.put(variant.getUuid(), attributeMap);
        }

        log.info("Found {} variants with attributes for product UUID: {}", resultMap.size(), productUuid);
        return resultMap;
    }


    @Override
    @Transactional(readOnly = true)
    public Map<String, Map<String, List<String>>> getProductAttributesListMap(String productUuid) {
        log.info("Getting product attributes list map for product UUID: {}", productUuid);

        // Step 1: Find product by UUID
        Product product = productRepository.findByUuid(productUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with UUID: " + productUuid));

        // Step 2: Find all ACTIVE variants for this product using product_id
        List<ProductVariant> variants = variantRepository.findByProductIdAndIsActive(product.getId(), true);
        log.debug("Found {} active variants for product ID: {}", variants.size(), product.getId());

        // Map to store results: Key = variant UUID, Value = Map of attribute name to list of values
        Map<String, Map<String, List<String>>> resultMap = new LinkedHashMap<>();

        // Step 3: Process each variant
        for (ProductVariant variant : variants) {

            // Step 4: Find attribute_value_ids for this variant using variant_id
            List<VariantAttributeValue> variantAttributeValues = variantAttributeValueRepository.findByVariantId(variant.getId());

            // Map to store attribute information for this variant (attribute name -> list of values)
            Map<String, List<String>> attributeMap = new LinkedHashMap<>();

            // Step 5: For each attribute value, find the product attribute
            for (VariantAttributeValue vav : variantAttributeValues) {
                // Get the attribute value using attribute_value_id
                ProductAttributeValue attributeValue = attributeValueRepository.findById(vav.getAttributeValueId())
                        .orElse(null);

                if (attributeValue != null) {

                    // Get the product attribute that matches with attribute_value
                    ProductAttribute attribute = attributeRepository.findById(attributeValue.getAttributeId())
                            .orElse(null);

                    if (attribute != null) {
                        log.debug("Found ProductAttributeList - ID: {}, Name: {}", attribute.getId(), attribute.getName());

                        // Add to map: accumulate multiple values for the same attribute name
                        attributeMap.computeIfAbsent(attribute.getName(), k -> new ArrayList<>())
                                .add(attributeValue.getValue());
                    }
                }
            }

            log.debug("Variant {} has {} attributes (list map): {}", variant.getUuid(), attributeMap.size(), attributeMap);

            // Fallback: If no attributes found in junction tables but attributeSummary exists, parse it
            if (attributeMap.isEmpty() && variant.getAttributeSummary() != null && !variant.getAttributeSummary().isEmpty()) {

                // Parse attributeSummary (format: "Color: Black, Size: M" or "Color: Black, Red, Size: M, S")
                String[] attributes = variant.getAttributeSummary().split(",");
                String currentAttributeName = null;

                for (String attr : attributes) {
                    String trimmedAttr = attr.trim();
                    if (!trimmedAttr.isEmpty()) {
                        if (trimmedAttr.contains(":")) {
                            // This is a new attribute (e.g., "Color: Black" or "Size: M")
                            String[] parts = trimmedAttr.split(":", 2);
                            if (parts.length == 2) {
                                currentAttributeName = parts[0].trim();
                                String attributeValue = parts[1].trim();
                                attributeMap.computeIfAbsent(currentAttributeName, k -> new ArrayList<>())
                                        .add(attributeValue);
                            }
                        } else if (currentAttributeName != null) {
                            // This is a continuation value (e.g., "Red" after "Color: Black")
                            attributeMap.computeIfAbsent(currentAttributeName, k -> new ArrayList<>())
                                    .add(trimmedAttr);
                        }
                    }
                }
            }

            // Deduplicate values in each list while preserving order
            Map<String, List<String>> deduplicatedMap = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> entry : attributeMap.entrySet()) {
                List<String> uniqueValues = new ArrayList<>(new LinkedHashSet<>(entry.getValue()));
                deduplicatedMap.put(entry.getKey(), uniqueValues);
            }

            // Step 6: Add to result map with variant UUID as key
            resultMap.put(variant.getUuid(), deduplicatedMap);
        }

        log.info("Found {} variants with attribute lists for product UUID: {}", resultMap.size(), productUuid);
        return resultMap;
    }

    // ========================================
    // PAYMENT METHOD HELPER METHODS
    // ========================================

    /**
     * Fetch payment method details from payment module via RabbitMQ (Map-based, no cross-module DTO).
     * Returns list of resolved payment method maps: { id, uuid, code, name, isActive }
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchPaymentMethodDetails(List<String> paymentMethodUuids) {
        log.info("Fetching payment method details via RabbitMQ for {} UUIDs", paymentMethodUuids.size());
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("requestId", UUID.randomUUID().toString());
            request.put("paymentMethodUuids", paymentMethodUuids);

            rabbitTemplate.setReplyTimeout(TimeUnit.SECONDS.toMillis(5));

            Object response = rabbitTemplate.convertSendAndReceive(
                    paymentExchange,
                    productPaymentMethodsLookupRoutingKey,
                    request
            );

            if (response instanceof Map) {
                Map<String, Object> responseMap = (Map<String, Object>) response;
                Boolean found = (Boolean) responseMap.get("found");
                if (Boolean.TRUE.equals(found)) {
                    List<Map<String, Object>> paymentMethods =
                            (List<Map<String, Object>>) responseMap.get("paymentMethods");
                    if (paymentMethods != null) {
                        // Filter only active payment methods
                        return paymentMethods.stream()
                                .filter(pm -> Boolean.TRUE.equals(pm.get("isActive")))
                                .collect(Collectors.toList());
                    }
                } else {
                    String errorMessage = (String) responseMap.get("errorMessage");
                    log.warn("Payment method lookup returned found=false. Error: {}", errorMessage);
                }
            } else {
                log.warn("Unexpected response type from payment module: {}",
                        response != null ? response.getClass().getName() : "null");
            }
        } catch (Exception e) {
            log.error("Error fetching payment method details via RabbitMQ", e);
        }
        return new ArrayList<>();
    }

    /**
     * Save payment method mappings for a product.
     * resolvedPaymentMethods: each map has { id (Long), uuid, code, name, isActive }
     */
    private void saveProductPaymentMethodMappings(Long productId, List<Map<String, Object>> resolvedPaymentMethods) {
        for (Map<String, Object> pm : resolvedPaymentMethods) {
            Long paymentMethodId = ((Number) pm.get("id")).longValue();
            String paymentMethodCode = (String) pm.get("code");

            if (paymentMethodMappingRepository.existsByProductIdAndPaymentMethodId(productId, paymentMethodId)) {
                log.debug("Payment method mapping already exists for product {} and payment method {}", productId, paymentMethodId);
                continue;
            }

            ProductPaymentMethodMapping mapping = ProductPaymentMethodMapping.builder()
                    .productId(productId)
                    .paymentMethodId(paymentMethodId)
                    .paymentMethodCode(paymentMethodCode)
                    .isActive(true)
                    .build();
            paymentMethodMappingRepository.save(mapping);
            log.debug("Saved payment method mapping: product={}, paymentMethodId={}, code={}",
                    productId, paymentMethodId, paymentMethodCode);
        }
        log.info("Saved {} payment method mappings for product ID: {}", resolvedPaymentMethods.size(), productId);
    }

    /**
     * Resolve payment method names for a product from its mapping table.
     * Returns display names (e.g. ["Cash on Delivery", "Credit/Debit Cards"]).
     */
    private List<String> resolveProductPaymentMethodNames(Long productId) {
        try {
            List<ProductPaymentMethodMapping> mappings =
                    paymentMethodMappingRepository.findByProductIdAndIsActive(productId, true);
            if (mappings == null || mappings.isEmpty()) {
                return new ArrayList<>();
            }
            // Use the payment method codes as display keys; for proper names we'd need
            // another RabbitMQ call — but we can derive human-readable names from codes
            // stored in the mapping table via a name-lookup call.
            List<Long> paymentMethodIds = mappings.stream()
                    .map(ProductPaymentMethodMapping::getPaymentMethodId)
                    .collect(Collectors.toList());

            return fetchPaymentMethodNamesById(paymentMethodIds, mappings);
        } catch (Exception e) {
            log.warn("Could not resolve payment method names for product {}: {}", productId, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Fetch payment method names by IDs via RabbitMQ.
     * Falls back to codes stored in mapping table if RabbitMQ call fails.
     */
    @SuppressWarnings("unchecked")
    private List<String> fetchPaymentMethodNamesById(List<Long> paymentMethodIds,
                                                      List<ProductPaymentMethodMapping> fallbackMappings) {
        try {
            // Convert IDs to UUIDs is not feasible without another call;
            // instead we re-use the codes snapshot for the lookup key but do a bulk name fetch.
            // We send the payment_method_ids directly — the payment consumer already handles UUID lookup,
            // so we store codes as snapshot and return them as display names when RabbitMQ is unavailable.
            // For a proper name, we re-call the payment module with the stored codes.
            Map<String, Object> request = new HashMap<>();
            request.put("requestId", UUID.randomUUID().toString());
            request.put("paymentMethodIds", paymentMethodIds);

            rabbitTemplate.setReplyTimeout(TimeUnit.SECONDS.toMillis(5));

            Object response = rabbitTemplate.convertSendAndReceive(
                    paymentExchange,
                    productPaymentMethodsNamesLookupRoutingKey,
                    request
            );

            if (response instanceof Map) {
                Map<String, Object> responseMap = (Map<String, Object>) response;
                Boolean found = (Boolean) responseMap.get("found");
                if (Boolean.TRUE.equals(found)) {
                    List<String> names = (List<String>) responseMap.get("names");
                    if (names != null && !names.isEmpty()) {
                        return names;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("RabbitMQ call for payment method names failed, falling back to codes: {}", e.getMessage());
        }

        // Fallback: return the stored code snapshots (e.g., COD, BANK_CARD)
        return fallbackMappings.stream()
                .map(ProductPaymentMethodMapping::getPaymentMethodCode)
                .collect(Collectors.toList());
    }

    /**
     * Convert an S3 object key (path) to a full public URL.
     * If the value is already a full URL (starts with "http"), return as-is.
     * If null or empty, return null.
     */
    private String toFullUrl(String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.isBlank()) {
            return null;
        }
        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
            return pathOrUrl;
        }
        return imageStorageService.getImageUrl(pathOrUrl, "thumbnail");
    }
}




