package org.psint.beyosclothing.modules.products.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.products.entity.Product;
import org.psint.beyosclothing.modules.products.entity.ProductGallery;
import org.psint.beyosclothing.modules.products.entity.ProductVariant;
import org.psint.beyosclothing.modules.products.entity.VariantGallery;
import org.psint.beyosclothing.modules.products.repository.ProductGalleryRepository;
import org.psint.beyosclothing.modules.products.repository.ProductRepository;
import org.psint.beyosclothing.modules.products.repository.ProductVariantRepository;
import org.psint.beyosclothing.modules.products.repository.VariantGalleryRepository;
import org.psint.beyosclothing.modules.products.service.ProductService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Consumer for POS Product List Requests
 * Handles paginated product list requests from POS module
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PosProductListConsumer {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductGalleryRepository galleryRepository;
    private final VariantGalleryRepository variantGalleryRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ProductService productService;

    @Value("${app.rabbitmq.exchange.inventory:beyos.exchange.inventory}")
    private String inventoryExchange;

    @RabbitListener(queues = "${app.rabbitmq.queue.pos-product-list-request:pos.product.list.request}")
    public Map<String, Object> handlePosProductListRequest(Map<String, Object> request) {
        log.info("🔍 [POS PRODUCT LIST] Received product list request: {}", request);

        try {
            String requestId = (String) request.get("requestId");
            String productUuid = (String) request.get("productUuid");
            String variantUuid = (String) request.get("variantUuid");

            // If variantUuid provided, return variant thumbnail via existing ProductService
            if (variantUuid != null && !variantUuid.trim().isEmpty()) {
                log.info("🔍 [POS VARIANT GALLERY] Handling variant gallery request for UUID: {}", variantUuid);
                try {
                    Object imgObj = productService.getVariantGalleryImages(variantUuid);

                    String thumbnailUrl = null;

                    if (imgObj != null) {
                        try {
                            // If it's a Map-like response
                            if (imgObj instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> map = (Map<String, Object>) imgObj;
                                Object t = map.get("thumbnailUrl");
                                if (t instanceof String) {
                                    thumbnailUrl = (String) t;
                                }
                            } else {
                                // Try getter method via reflection: getThumbnailUrl()
                                try {
                                    Object t = imgObj.getClass().getMethod("getThumbnailUrl").invoke(imgObj);
                                    if (t instanceof String) {
                                        thumbnailUrl = (String) t;
                                    }
                                } catch (NoSuchMethodException name) {
                                    // Try field access as fallback
                                    try {
                                        java.lang.reflect.Field f = imgObj.getClass().getDeclaredField("thumbnailUrl");
                                        f.setAccessible(true);
                                        Object t = f.get(imgObj);
                                        if (t instanceof String) {
                                            thumbnailUrl = (String) t;
                                        }
                                    } catch (Exception ignored) {
                                        // leave thumbnailUrl null
                                    }
                                }
                            }
                        } catch (Exception e) {
                            log.debug("Error extracting thumbnailUrl from productService response", e);
                        }
                    }

                    Map<String, Object> resp = new HashMap<>();
                    resp.put("requestId", requestId);
                    resp.put("success", true);
                    resp.put("thumbnailUrl", thumbnailUrl);
                    return resp;
                } catch (Exception e) {
                    log.error("❌ Error fetching variant gallery for uuid: {}", variantUuid, e);
                    return buildSingleProductErrorResponse(requestId, "Variant not found");
                }
            }

            // If productUuid is provided, handle single product request
            if (productUuid != null && !productUuid.trim().isEmpty()) {
                log.info("🔍 [POS SINGLE PRODUCT] Handling single product request for UUID: {}", productUuid);
                return handleSingleProductRequest(requestId, productUuid);
            }

            // Extract pagination parameters for list request
            Integer page = request.get("page") != null ? ((Number) request.get("page")).intValue() : 0;
            Integer size = request.get("size") != null ? ((Number) request.get("size")).intValue() : 20;

            log.debug("Fetching products - Page: {}, Size: {}", page, size);

            // Get all active published products
            List<Product> allProducts = productRepository.findAllPublishedProducts();

            // Calculate pagination
            int start = page * size;
            int end = Math.min(start + size, allProducts.size());
            int totalPages = (int) Math.ceil((double) allProducts.size() / size);

            if (start >= allProducts.size()) {
                log.warn("Page {} out of range. Total products: {}", page, allProducts.size());
                return buildResponse(requestId, Collections.emptyList(), page, totalPages, (long) allProducts.size(), size, true);
            }

            List<Product> pageProducts = allProducts.subList(start, end);

            // Convert to response DTOs
            List<Map<String, Object>> productItems = pageProducts.stream()
                    .map(this::convertToProductItem)
                    .collect(Collectors.toList());

            log.info("POS product list request successful - Page: {}, Total: {}, Returned: {}",
                    page, allProducts.size(), productItems.size());

            return buildResponse(requestId, productItems, page, totalPages, (long) allProducts.size(), size, true);

        } catch (Exception e) {
            log.error("Error processing POS product list request", e);
            return buildErrorResponse(
                    (String) request.get("requestId"),
                    "Error fetching products: " + e.getMessage()
            );
        }
    }

    /**
     * Handle single product request by UUID
     */
    private Map<String, Object> handleSingleProductRequest(String requestId, String productUuid) {
        try {
            // Find product by UUID
            Optional<Product> productOpt = productRepository.findByUuid(productUuid);

            if (productOpt.isEmpty()) {
                log.warn("Product not found with UUID: {}", productUuid);
                return buildSingleProductErrorResponse(requestId, "Product not found");
            }

            Product product = productOpt.get();

            // Check if product is active and published
            if (!product.getIsActive() || !product.getIsPublish()) {
                log.warn("Product {} is not active or not published", productUuid);
                return buildSingleProductErrorResponse(requestId, "Product not available");
            }

            // Convert to product item (includes stock fetching via RabbitMQ)
            Map<String, Object> productItem = convertToProductItem(product);

            // Add id to the product item
            productItem.put("id", product.getId());

            // Fetch gallery images
            try {
                log.debug("🖼️ Fetching gallery images for product ID: {}", product.getId());
                List<ProductGallery> galleryImages = galleryRepository.findByProductIdAndIsActiveTrueOrderBySortOrderAsc(product.getId());

                List<Map<String, Object>> galleryList = galleryImages.stream()
                        .map(gallery -> {
                            Map<String, Object> galleryMap = new HashMap<>();
                            galleryMap.put("uuid", gallery.getUuid());
                            galleryMap.put("mediaUrl", gallery.getMediaName());
                            galleryMap.put("sortOrder", gallery.getSortOrder());
                            galleryMap.put("altText", gallery.getAltText());
                            galleryMap.put("mimeType", gallery.getMimeType());
                            return galleryMap;
                        })
                        .collect(Collectors.toList());

                productItem.put("galleryImages", galleryList);
                log.info("✅ [GALLERY] Fetched {} gallery images for product UUID: {}", galleryList.size(), productUuid);
            } catch (Exception e) {
                log.error("❌ Error fetching gallery images for product UUID: {}", productUuid, e);
                productItem.put("galleryImages", Collections.emptyList());
            }

            // Fetch variant gallery images for all variants
            try {
                log.debug("🖼️ Fetching variant gallery images for product ID: {}", product.getId());
                List<ProductVariant> variants = variantRepository.findByProductIdAndIsActive(product.getId(), true);

                Map<String, List<Map<String, Object>>> variantGalleriesMap = new HashMap<>();

                for (ProductVariant variant : variants) {
                    List<VariantGallery> variantGalleries = variantGalleryRepository.findByVariantIdOrderBySortOrder(variant.getId());

                    List<Map<String, Object>> variantGalleryList = variantGalleries.stream()
                            .map(gallery -> {
                                Map<String, Object> galleryMap = new HashMap<>();
                                galleryMap.put("uuid", gallery.getUuid());
                                galleryMap.put("mediaUrl", gallery.getMediaName());
                                galleryMap.put("sortOrder", gallery.getSortOrder());
                                galleryMap.put("altText", gallery.getAltText());
                                galleryMap.put("mimeType", gallery.getMimeType());
                                return galleryMap;
                            })
                            .collect(Collectors.toList());

                    if (!variantGalleryList.isEmpty()) {
                        variantGalleriesMap.put(variant.getUuid(), variantGalleryList);
                    }
                }

                productItem.put("variantGalleries", variantGalleriesMap);
                log.info("✅ [VARIANT GALLERY] Fetched variant galleries for {} variants", variantGalleriesMap.size());
            } catch (Exception e) {
                log.error("❌ Error fetching variant gallery images for product UUID: {}", productUuid, e);
                productItem.put("variantGalleries", Collections.emptyMap());
            }

            // Fetch variant attributes using ProductService
            try {
                log.debug("🔍 Fetching variant attributes for product UUID: {}", productUuid);

                Map<String, Map<String, String>> variantAttributesMap = productService.getProductAttributesMap(productUuid);
                Map<String, Map<String, List<String>>> variantAttributesListMap = productService.getProductAttributesListMap(productUuid);
                Map<String, Map<String, String>> variantPricesMap = buildVariantPricesMap(product.getId());

                productItem.put("variantAttributesMap", variantAttributesMap);
                productItem.put("variantAttributesListMap", variantAttributesListMap);
                productItem.put("variantPricesMap", variantPricesMap);

                log.info("✅ Fetched variant attributes - Map size: {}, ListMap size: {}, PriceMap size: {}",
                        variantAttributesMap.size(), variantAttributesListMap.size(), variantPricesMap.size());
            } catch (Exception e) {
                log.error("❌ Error fetching variant attributes for product UUID: {}", productUuid, e);
                // Set empty maps as fallback
                productItem.put("variantAttributesMap", Collections.emptyMap());
                productItem.put("variantAttributesListMap", Collections.emptyMap());
                productItem.put("variantPricesMap", Collections.emptyMap());
            }

            log.info("✅ [POS SINGLE PRODUCT] Product details fetched successfully for UUID: {} with availableQuantity: {}",
                    productUuid, productItem.get("availableQuantity"));

            return buildSingleProductSuccessResponse(requestId, productItem);

        } catch (Exception e) {
            log.error("❌ [POS SINGLE PRODUCT] Error processing request for UUID: {}", productUuid, e);
            return buildSingleProductErrorResponse(requestId, "Error fetching product details: " + e.getMessage());
        }
    }

    private Map<String, Object> convertToProductItem(Product product) {
        Map<String, Object> item = new HashMap<>();

        item.put("uuid", product.getUuid());
        item.put("title", product.getTitle());
        item.put("sku", product.getSku());
        item.put("thumbnailUrl", product.getThumbnailUrl());
        item.put("showcasePrice", product.getShowcasePrice());
        item.put("productType", product.getProductType().name());
        item.put("isActive", product.getIsActive());

        // Determine sale price and available quantity based on product type
        if (Product.ProductType.VARIABLE.equals(product.getProductType())) {
            // For VARIABLE products, use default variant if available
            ProductVariant defaultVariant = null;

            if (product.getDefaultVariantId() != null) {
                // Use the default variant ID from product
                defaultVariant = variantRepository.findById(product.getDefaultVariantId()).orElse(null);
            }

            // If no default variant found, get the first active variant
            if (defaultVariant == null) {
                List<ProductVariant> variants = variantRepository.findByProductIdAndIsActive(product.getId(), true);
                item.put("hasVariants", !variants.isEmpty());

                if (!variants.isEmpty()) {
                    defaultVariant = variants.get(0);
                }
            } else {
                item.put("hasVariants", true);
            }

            if (defaultVariant != null) {
                BigDecimal salePrice = defaultVariant.getSalePrice() != null
                        ? defaultVariant.getSalePrice()
                        : defaultVariant.getShowcasePrice();
                item.put("salePrice", salePrice);

                // Get stock for default variant via RabbitMQ
                Integer stock = getStockQuantityViaRabbitMQ(product.getId(), defaultVariant.getId());
                item.put("availableQuantity", stock);
            } else {
                item.put("salePrice", product.getShowcasePrice());
                item.put("availableQuantity", 0);
                item.put("hasVariants", false);
            }
        } else {
            // For SIMPLE products
            item.put("hasVariants", false);
            BigDecimal salePrice = product.getSalePrice() != null
                    ? product.getSalePrice()
                    : product.getShowcasePrice();
            item.put("salePrice", salePrice);

            // Get stock for simple product (variantId = null) via RabbitMQ
            Integer stock = getStockQuantityViaRabbitMQ(product.getId(), null);
            item.put("availableQuantity", stock);
        }

        return item;
    }

    private Map<String, Map<String, String>> buildVariantPricesMap(Long productId) {
        List<ProductVariant> variants = variantRepository.findByProductIdAndIsActive(productId, true);
        Map<String, Map<String, String>> variantPricesMap = new LinkedHashMap<>();

        for (ProductVariant variant : variants) {
            Map<String, String> prices = new LinkedHashMap<>();
            BigDecimal showcasePrice = variant.getShowcasePrice();
            BigDecimal salePrice = variant.getSalePrice();
            BigDecimal effectivePrice = salePrice != null ? salePrice : showcasePrice;

            prices.put("price", formatPrice(effectivePrice));
            prices.put("showcasePrice", formatPrice(showcasePrice));
            prices.put("salePrice", formatPrice(salePrice));
            variantPricesMap.put(variant.getUuid(), prices);
        }

        return variantPricesMap;
    }

    private String formatPrice(BigDecimal price) {
        return price != null ? price.toPlainString() : null;
    }

    /**
     * Fetch stock quantity from Inventory module via RabbitMQ
     */
    private Integer getStockQuantityViaRabbitMQ(Long productId, Long variantId) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("productId", productId);
            request.put("variantId", variantId);
            request.put("requestedQuantity", 1); // Dummy value for checking stock

            rabbitTemplate.setReplyTimeout(TimeUnit.SECONDS.toMillis(3));

            Object response = rabbitTemplate.convertSendAndReceive(
                    inventoryExchange,
                    "inventory.stock.check",
                    request
            );

            if (response instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> responseMap = (Map<String, Object>) response;

                Boolean found = (Boolean) responseMap.get("found");
                if (Boolean.TRUE.equals(found)) {
                    Object availableStockObj = responseMap.get("availableStock");
                    if (availableStockObj instanceof Number) {
                        return ((Number) availableStockObj).intValue();
                    }
                }
            } else if (response != null) {
                // Handle StockCheckResponse object using reflection
                try {
                    Boolean found = (Boolean) response.getClass().getMethod("getFound").invoke(response);
                    if (Boolean.TRUE.equals(found)) {
                        Object availableStock = response.getClass().getMethod("getAvailableStock").invoke(response);
                        if (availableStock instanceof Integer) {
                            return (Integer) availableStock;
                        }
                    }
                } catch (Exception e) {
                    log.debug("Error extracting stock from response via reflection", e);
                }
            }

            return 0;
        } catch (Exception e) {
            log.error("Error fetching stock for product ID: {}, variant ID: {} via RabbitMQ", productId, variantId, e);
            return 0;
        }
    }

    private Map<String, Object> buildResponse(String requestId, List<Map<String, Object>> products,
                                              Integer currentPage, Integer totalPages,
                                              Long totalProducts, Integer pageSize, boolean success) {
        Map<String, Object> response = new HashMap<>();
        response.put("requestId", requestId);
        response.put("success", success);
        response.put("products", products);
        response.put("currentPage", currentPage);
        response.put("totalPages", totalPages);
        response.put("totalProducts", totalProducts);
        response.put("pageSize", pageSize);
        return response;
    }

    private Map<String, Object> buildErrorResponse(String requestId, String errorMessage) {
        Map<String, Object> response = new HashMap<>();
        response.put("requestId", requestId);
        response.put("success", false);
        response.put("errorMessage", errorMessage);
        response.put("products", Collections.emptyList());
        response.put("currentPage", 0);
        response.put("totalPages", 0);
        response.put("totalProducts", 0L);
        response.put("pageSize", 0);
        return response;
    }

    private Map<String, Object> buildSingleProductSuccessResponse(String requestId, Map<String, Object> productItem) {
        Map<String, Object> response = new HashMap<>();
        response.put("requestId", requestId);
        response.put("success", true);
        response.put("product", productItem);
        return response;
    }

    private Map<String, Object> buildSingleProductErrorResponse(String requestId, String errorMessage) {
        Map<String, Object> response = new HashMap<>();
        response.put("requestId", requestId);
        response.put("success", false);
        response.put("errorMessage", errorMessage);
        return response;
    }
}
