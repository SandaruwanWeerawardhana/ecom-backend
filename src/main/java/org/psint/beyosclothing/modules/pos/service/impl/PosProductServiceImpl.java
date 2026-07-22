package org.psint.beyosclothing.modules.pos.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.pos.dto.response.ImageThumbnailUrlPosResponse;
import org.psint.beyosclothing.modules.pos.dto.response.PosProductListResponse;
import org.psint.beyosclothing.modules.pos.dto.response.PosProductPopupResponse;
import org.psint.beyosclothing.modules.pos.dto.response.PosProductSearchResponse;
import org.psint.beyosclothing.modules.pos.entity.PosProductCacheEntity;
import org.psint.beyosclothing.modules.pos.repository.PosProductCacheRepository;
import org.psint.beyosclothing.modules.pos.service.PosProductService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PosProductServiceImpl implements PosProductService {

    private final RabbitTemplate rabbitTemplate;
    private final PosProductCacheRepository posProductCacheRepository;

    @Value("${app.rabbitmq.exchange.product:product.exchange}")
    private String productExchange;

    @Value("${app.rabbitmq.exchange.inventory:inventory.exchange}")
    private String inventoryExchange;

    @Override
    public List<PosProductSearchResponse> searchProducts(String query, Integer limit) {
        return List.of();
    }

    @Override
    public PosProductSearchResponse getProductForPos(Long productId) {
        return null;
    }

    @Override
    public PosProductListResponse getAllActiveProducts(Integer page, Integer size) {
        log.info("Fetching all active products from POS product cache - Page: {}, Size: {}", page, size);

        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "syncedAt"));
            Page<PosProductCacheEntity> cachePage = posProductCacheRepository.findByIsActiveTrue(pageable);

            List<PosProductListResponse.PosProductItem> products = cachePage.getContent().stream()
                    .map(this::mapCacheEntityToProductItem)
                    .collect(Collectors.toList());

            log.info("Retrieved {} active products (total {}) from POS product cache",
                    products.size(), cachePage.getTotalElements());

            return PosProductListResponse.builder()
                    .products(products)
                    .currentPage(cachePage.getNumber())
                    .totalPages(cachePage.getTotalPages())
                    .totalProducts(cachePage.getTotalElements())
                    .pageSize(cachePage.getSize())
                    .build();

        } catch (Exception e) {
            log.error("Error fetching products from POS product cache", e);
            return buildEmptyResponse(page, size);
        }
    }

    @Override
    public PosProductPopupResponse getProductForPosByUuid(String productUuid) {
        log.info("Fetching product details via RabbitMQ - UUID: {}", productUuid);

        try {
            // Build request
            Map<String, Object> request = new HashMap<>();
            request.put("requestId", UUID.randomUUID().toString());
            request.put("productUuid", productUuid);

            log.debug("Sending POS product details request to product exchange via pos.product.list.request");

            // Set timeout
            rabbitTemplate.setReplyTimeout(TimeUnit.SECONDS.toMillis(10));

            // Send request and wait for response using pos.product.list.request (reusing PosProductListConsumer)
            Object response = rabbitTemplate.convertSendAndReceive(
                    productExchange,
                    "pos.product.list.request",
                    request
            );

            log.debug("Received response from product module: {}", response != null ? response.getClass().getName() : "null");

            if (response instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> responseMap = (Map<String, Object>) response;

                Boolean success = (Boolean) responseMap.get("success");
                if (Boolean.TRUE.equals(success)) {
                    return parseProductDetailsResponseToPopup(responseMap);
                } else {
                    String errorMessage = (String) responseMap.get("errorMessage");
                    log.error("Product details request failed: {}", errorMessage);
                    return null;
                }
            } else {
                log.error("Invalid response type from product module");
                return null;
            }

        } catch (Exception e) {
            log.error("Error fetching product details via RabbitMQ for UUID: {}", productUuid, e);
            return null;
        }
    }

    @Override
    public ImageThumbnailUrlPosResponse getVariantGalleryImages(String uuid) {
        log.info("Fetching variant gallery thumbnail via RabbitMQ for variant UUID: {}", uuid);

        try {
            // Build request payload expected by product module
            Map<String, Object> request = new HashMap<>();
            request.put("requestId", UUID.randomUUID().toString());
            request.put("variantUuid", uuid);

            // Set a reasonable RPC timeout
            rabbitTemplate.setReplyTimeout(TimeUnit.SECONDS.toMillis(10));

            log.debug("Sending variant gallery request to product exchange for variantUuid={}", uuid);

            // Send RPC-style request to product module and wait for response
            Object response = rabbitTemplate.convertSendAndReceive(
                    productExchange,
                    "pos.product.list.request",
                    request
            );

            log.debug("Received variant gallery response (type={}): {}",
                    response != null ? response.getClass().getName() : "null",
                    response);

            switch (response) {
                case null -> {
                    log.warn("No response from product module for variantUuid={}", uuid);
                    return ImageThumbnailUrlPosResponse.builder().thumbnailUrl(null).build();
                }


                // Handle different possible response formats
                case Map map1 -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) response;

                    Object thumbnailObj = null;
                    if (map.containsKey("thumbnailUrl")) {
                        thumbnailObj = map.get("thumbnailUrl");
                    } else if (map.containsKey("data") && map.get("data") instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> data = (Map<String, Object>) map.get("data");
                        thumbnailObj = data.get("thumbnailUrl");
                    } else if (map.containsKey("image") && map.get("image") instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> image = (Map<String, Object>) map.get("image");
                        thumbnailObj = image.get("thumbnailUrl");
                    }

                    if (thumbnailObj instanceof String s) {
                        return ImageThumbnailUrlPosResponse.builder().thumbnailUrl(s).build();
                    }

                    // If no direct thumbnail found, try to find first String value in the map as a fallback
                    for (Object v : map.values()) {
                        if (v instanceof String s) {
                            return ImageThumbnailUrlPosResponse.builder().thumbnailUrl(s).build();
                        }
                        if (v instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> inner = (Map<String, Object>) v;
                            Object maybe = inner.get("thumbnailUrl");
                            if (maybe instanceof String s2) {
                                return ImageThumbnailUrlPosResponse.builder().thumbnailUrl(s2).build();
                            }
                        }
                    }

                    log.warn("Unable to extract thumbnailUrl from product module response for variantUuid={}", uuid);
                    return ImageThumbnailUrlPosResponse.builder().thumbnailUrl(null).build();
                }
                case String s -> {
                    // Product module may return the URL string directly
                    return ImageThumbnailUrlPosResponse.builder().thumbnailUrl(s).build();
                    // Product module may return the URL string directly
                }
                default -> {
                    log.warn("Unexpected response type from product module for variantUuid={}: {}", uuid, response.getClass().getName());
                    return ImageThumbnailUrlPosResponse.builder().thumbnailUrl(null).build();
                }
            }

        } catch (Exception e) {
            log.error("Error fetching variant gallery thumbnail via RabbitMQ for variantUuid={}", uuid, e);
            return ImageThumbnailUrlPosResponse.builder().thumbnailUrl(null).build();
        }
    }

    /**
     * Fetch available quantity from inventory module via RabbitMQ
     * Reuses the existing inventory.stock.check queue
     */

    private PosProductListResponse.PosProductItem mapCacheEntityToProductItem(PosProductCacheEntity entity) {
        return PosProductListResponse.PosProductItem.builder()
                .uuid(entity.getUuid())
                .title(entity.getTitle())
                .sku(entity.getSku())
                .thumbnailUrl(entity.getThumbnailUrl())
                .showcasePrice(entity.getShowcasePrice())
                .salePrice(entity.getSalePrice())
                .availableQuantity(entity.getStockAvailable() != null ? entity.getStockAvailable() : 0)
                .productType(Boolean.TRUE.equals(entity.getHasVariants()) ? "VARIABLE" : "SIMPLE")
                .isActive(entity.getIsActive())
                .hasVariants(Boolean.TRUE.equals(entity.getHasVariants()))
                .build();
    }

    private PosProductPopupResponse parseProductDetailsResponseToPopup(Map<String, Object> responseMap) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> productData = (Map<String, Object>) responseMap.get("product");

            if (productData == null) {
                log.warn("Product data is null in response");
                return null;
            }

            String productUuid = (String) productData.get("uuid");

            boolean isActive = (Boolean) productData.getOrDefault("isActive", true);
            if (!isActive) {
                log.warn("Requested product is inactive and will not be returned. UUID: {}", productUuid);
                return null;
            }

            log.debug("📦 [PARSE RESPONSE] Parsing product data for UUID: {}", productUuid);

            // Get available quantity directly from product response (already fetched by PosProductListConsumer)
            Integer availableQuantity = productData.get("availableQuantity") != null
                    ? ((Number) productData.get("availableQuantity")).intValue()
                    : 0;

            log.info("📦 [PARSE RESPONSE] Product UUID: {}, availableQuantity: {}", productUuid, availableQuantity);

            // Build a combined map for product-level and variant-level galleries
            Map<String, List<PosProductPopupResponse.GalleryImage>> galleryImagesMap = new HashMap<>();

            // Variant galleries (map under "variantGalleries")
            Object variantGalleriesData = productData.get("variantGalleries");
            if (variantGalleriesData instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, List<Map<String, Object>>> variantGalleriesRaw = (Map<String, List<Map<String, Object>>>) variantGalleriesData;
                for (Map.Entry<String, List<Map<String, Object>>> entry : variantGalleriesRaw.entrySet()) {
                    String variantUuid = entry.getKey();
                    List<Map<String, Object>> imagesData = entry.getValue();
                    List<PosProductPopupResponse.GalleryImage> variantImages = imagesData.stream()
                            .map(imageMap -> {
                                int sortOrder = 0;
                                Object sortObj = imageMap.get("sortOrder");
                                if (sortObj instanceof Number number) {
                                    sortOrder = number.intValue();
                                }
                                return PosProductPopupResponse.GalleryImage.builder()
                                        .uuid((String) imageMap.get("uuid"))
                                        .mediaUrl((String) imageMap.get("mediaUrl"))
                                        .sortOrder(sortOrder)
                                        .altText((String) imageMap.get("altText"))
                                        .mimeType((String) imageMap.get("mimeType"))
                                        .build();
                            })
                           .toList();

                    if (!variantImages.isEmpty()) {
                        galleryImagesMap.put(variantUuid, variantImages);
                        log.debug("🖼️ [PARSE VARIANT GALLERY] Variant UUID: {}, Images: {}", variantUuid, variantImages.size());
                    }
                }
                log.info("✅ [PARSE VARIANT GALLERY] Parsed variant galleries for {} variants", galleryImagesMap.size());
            } else {
                log.debug("🖼️ [PARSE VARIANT GALLERY] No variant galleries data found or invalid format");
            }

            // Extract variant attributes if present
            @SuppressWarnings("unchecked")
            Map<String, Map<String, String>> variantAttributesMap =
                    (Map<String, Map<String, String>>) productData.getOrDefault("variantAttributesMap", Collections.emptyMap());

            @SuppressWarnings("unchecked")
            Map<String, Map<String, List<String>>> variantAttributesListMap =
                    (Map<String, Map<String, List<String>>>) productData.getOrDefault("variantAttributesListMap", Collections.emptyMap());

            @SuppressWarnings("unchecked")
            Map<String, Map<String, String>> variantPricesMap =
                    (Map<String, Map<String, String>>) productData.getOrDefault("variantPricesMap", Collections.emptyMap());

            log.debug("📦 [PARSE RESPONSE] Extracted attributes - Map entries: {}, ListMap entries: {}, PriceMap entries: {}",
                    variantAttributesMap.size(), variantAttributesListMap.size(), variantPricesMap.size());

            // Format prices
            String showcasePriceStr = null;
            String salePriceStr = null;

            Object showcasePrice = productData.get("showcasePrice");
            if (showcasePrice != null) {
                BigDecimal showcasePriceBd = convertToBigDecimal(showcasePrice);
                showcasePriceStr = String.format("%.2f", showcasePriceBd);
            }

            Object salePrice = productData.get("salePrice");
            if (salePrice != null) {
                BigDecimal salePriceBd = convertToBigDecimal(salePrice);
                salePriceStr = String.format("%.2f", salePriceBd);
            }

            return PosProductPopupResponse.builder()
                    .uuid(productUuid)
                    .title((String) productData.get("title"))
                    .sku((String) productData.get("sku"))
                    .thumbnailUrl((String) productData.get("thumbnailUrl"))
                    .showcasePrice(showcasePriceStr)
                    .salePrice(salePriceStr)
                    .availableQuantity(availableQuantity)
                    .productType((String) productData.get("productType"))
                    .isActive(isActive)
                    .hasVariants((Boolean) productData.getOrDefault("hasVariants", false))
                    .galleryImages(galleryImagesMap)
                    .variantAttributesMap(variantAttributesMap)
                    .variantAttributesListMap(variantAttributesListMap)
                    .variantPricesMap(variantPricesMap)
                    .build();

        } catch (Exception e) {
            log.error("Error parsing product details response to popup", e);
            return null;
        }
    }

    private BigDecimal convertToBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString());
        }
        return BigDecimal.ZERO;
    }

    private PosProductListResponse buildEmptyResponse(Integer page, Integer size) {
        return PosProductListResponse.builder()
                .products(Collections.emptyList())
                .currentPage(page)
                .totalPages(0)
                .totalProducts(0L)
                .pageSize(size)
                .build();
    }
}
