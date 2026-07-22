package org.psint.beyosclothing.modules.products.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.inventory.dto.external.InventoryProductBulkLookupResponse;
import org.psint.beyosclothing.modules.inventory.dto.external.InventoryProductLookupRequest;
import org.psint.beyosclothing.modules.inventory.dto.external.InventoryProductLookupResponse;
import org.psint.beyosclothing.modules.products.entity.Product;
import org.psint.beyosclothing.modules.products.entity.ProductCategory;
import org.psint.beyosclothing.modules.products.entity.ProductVariant;
import org.psint.beyosclothing.modules.products.repository.ProductCategoryRepository;
import org.psint.beyosclothing.modules.products.repository.ProductRepository;
import org.psint.beyosclothing.modules.products.repository.ProductVariantRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Consumer that handles product-info lookup requests originating from the
 * Inventory module.
 *
 * Queue  : inventory.product.lookup.request
 * Pattern: RabbitMQ request-reply (convertSendAndReceive)
 *
 * The request / response DTOs are defined in the Inventory module so that
 * the Inventory module does NOT need to import any Product-module class —
 * the Product module is the only side that holds the import, which is the
 * correct direction (product knows about inventory's DTOs, inventory does
 * not know about product's entities).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryProductLookupConsumer {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductCategoryRepository productCategoryRepository;

    @RabbitListener(queues = "${app.rabbitmq.queue.inventory-product-lookup-request:inventory.product.lookup.request}")
    public InventoryProductLookupResponse handleInventoryProductLookupRequest(InventoryProductLookupRequest request) {
        log.debug("🔍 [INVENTORY LOOKUP] Received product lookup request - productId: {}, variantId: {}",
                request.getProductId(), request.getVariantId());

        try {
            Product product = productRepository.findById(request.getProductId()).orElse(null);

            if (product == null) {
                log.warn("Product not found by ID: {}", request.getProductId());
                return InventoryProductLookupResponse.builder()
                        .requestId(request.getRequestId())
                        .found(false)
                        .errorMessage("Product not found: " + request.getProductId())
                        .build();
            }

            String category = resolveCategorySlug(product.getCategoryId(), Map.of());

            // Default to product-level SKU and inventory status; override with variant values if variantId supplied
            String sku = product.getSku();
            String inventoryStatus = product.getInventoryStatus() != null
                    ? product.getInventoryStatus().name() : "IN_STOCK";

            if (request.getVariantId() != null) {
                ProductVariant variant = variantRepository.findById(request.getVariantId()).orElse(null);
                if (variant != null && variant.getProductId().equals(product.getId())) {
                    sku = variant.getSku();
                    // Use variant-level inventory_status from product_variants table
                    inventoryStatus = variant.getInventoryStatus() != null
                            ? variant.getInventoryStatus().name() : inventoryStatus;
                    // Expose attribute summary to inventory module
                    String attributeSummary = variant.getAttributeSummary();

                    log.debug("Variant attributeSummary: {}", attributeSummary);

                    return InventoryProductLookupResponse.builder()
                            .requestId(request.getRequestId())
                            .found(true)
                            .productTitle(product.getTitle())
                            .productType(product.getProductType() != null ? product.getProductType().name() : "SIMPLE")
                            .categoryId(product.getCategoryId())
                            .category(category)
                            .image(resolveImage(product, variant))
                            .sku(sku)
                            .inventoryStatus(inventoryStatus)
                            .attributeSummary(attributeSummary)
                            .build();
                } else {
                    log.warn("Variant not found or does not belong to product - variantId: {}, productId: {}",
                            request.getVariantId(), request.getProductId());
                }
            }

            log.debug("Product info resolved - title: {}, sku: {}, inventoryStatus: {}",
                    product.getTitle(), sku, inventoryStatus);

            return InventoryProductLookupResponse.builder()
                    .requestId(request.getRequestId())
                    .found(true)
                    .productTitle(product.getTitle())
                    .productType(product.getProductType() != null ? product.getProductType().name() : "SIMPLE")
                    .categoryId(product.getCategoryId())
                    .category(category)
                    .image(resolveImage(product, null))
                    .sku(sku)
                    .inventoryStatus(inventoryStatus)
                    .attributeSummary(null)
                    .build();

        } catch (Exception e) {
            log.error("Error handling inventory product lookup request for productId: {}",
                    request.getProductId(), e);
            return InventoryProductLookupResponse.builder()
                    .requestId(request.getRequestId())
                    .found(false)
                    .errorMessage("Error: " + e.getMessage())
                    .build();
        }
    }

    @RabbitListener(queues = "${app.rabbitmq.queue.inventory-product-bulk-lookup-request:inventory.product.bulk.lookup.request}")
    public InventoryProductBulkLookupResponse handleInventoryProductBulkLookupRequest(InventoryProductLookupRequest request) {
        List<InventoryProductLookupRequest.LookupItem> items = request.getItems() == null
                ? Collections.emptyList()
                : request.getItems();

        try {
            Set<Long> productIds = items.stream()
                    .map(InventoryProductLookupRequest.LookupItem::getProductId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Set<Long> variantIds = items.stream()
                    .map(InventoryProductLookupRequest.LookupItem::getVariantId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            Map<Long, Product> productsById = productRepository.findAllById(productIds).stream()
                    .collect(Collectors.toMap(Product::getId, Function.identity()));
            Map<Long, ProductVariant> variantsById = variantRepository.findAllById(variantIds).stream()
                    .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));
            Set<Long> categoryIds = productsById.values().stream()
                    .map(Product::getCategoryId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Map<Long, ProductCategory> categoriesById = productCategoryRepository.findAllById(categoryIds).stream()
                    .collect(Collectors.toMap(ProductCategory::getId, Function.identity()));

            Map<String, InventoryProductLookupResponse> responses = new LinkedHashMap<>();
            for (InventoryProductLookupRequest.LookupItem item : items) {
                responses.put(productLookupKey(item.getProductId(), item.getVariantId()),
                        buildLookupResponse(request.getRequestId(), item.getProductId(), item.getVariantId(),
                                productsById, variantsById, categoriesById));
            }

            return InventoryProductBulkLookupResponse.builder()
                    .requestId(request.getRequestId())
                    .success(true)
                    .items(responses)
                    .build();
        } catch (Exception e) {
            log.error("Error handling bulk inventory product lookup request", e);
            return InventoryProductBulkLookupResponse.builder()
                    .requestId(request.getRequestId())
                    .success(false)
                    .errorMessage("Error: " + e.getMessage())
                    .items(Collections.emptyMap())
                    .build();
        }
    }

    private InventoryProductLookupResponse buildLookupResponse(
            String requestId,
            Long productId,
            Long variantId,
            Map<Long, Product> productsById,
            Map<Long, ProductVariant> variantsById,
            Map<Long, ProductCategory> categoriesById) {
        Product product = productsById.get(productId);
        if (product == null) {
            return InventoryProductLookupResponse.builder()
                    .requestId(requestId)
                    .found(false)
                    .productTitle("Unknown Product")
                    .productType("SIMPLE")
                    .inventoryStatus("IN_STOCK")
                    .errorMessage("Product not found: " + productId)
                    .build();
        }

        String sku = product.getSku();
        String inventoryStatus = product.getInventoryStatus() != null
                ? product.getInventoryStatus().name() : "IN_STOCK";
        String attributeSummary = null;
        ProductVariant selectedVariant = null;

        if (variantId != null) {
            ProductVariant variant = variantsById.get(variantId);
            if (variant != null && variant.getProductId().equals(product.getId())) {
                selectedVariant = variant;
                sku = variant.getSku();
                inventoryStatus = variant.getInventoryStatus() != null
                        ? variant.getInventoryStatus().name() : inventoryStatus;
                attributeSummary = variant.getAttributeSummary();
            } else {
                log.warn("Variant not found or does not belong to product - variantId: {}, productId: {}",
                        variantId, productId);
            }
        }

        return InventoryProductLookupResponse.builder()
                .requestId(requestId)
                .found(true)
                .productTitle(product.getTitle())
                .productType(product.getProductType() != null ? product.getProductType().name() : "SIMPLE")
                .categoryId(product.getCategoryId())
                .category(resolveCategorySlug(product.getCategoryId(), categoriesById))
                .image(resolveImage(product, selectedVariant))
                .sku(sku)
                .inventoryStatus(inventoryStatus)
                .attributeSummary(attributeSummary)
                .build();
    }

    private String resolveCategorySlug(Long categoryId, Map<Long, ProductCategory> categoriesById) {
        if (categoryId == null) {
            return null;
        }
        ProductCategory category = categoriesById.get(categoryId);
        if (category == null) {
            category = productCategoryRepository.findById(categoryId).orElse(null);
        }
        return category != null ? category.getSlug() : null;
    }

    private String resolveImage(Product product, ProductVariant variant) {
        if (variant != null && variant.getThumbnailUrl() != null && !variant.getThumbnailUrl().isBlank()) {
            return variant.getThumbnailUrl();
        }
        return product != null ? product.getThumbnailUrl() : null;
    }

    private String productLookupKey(Long productId, Long variantId) {
        return productId + ":" + (variantId == null ? "null" : variantId);
    }
}

