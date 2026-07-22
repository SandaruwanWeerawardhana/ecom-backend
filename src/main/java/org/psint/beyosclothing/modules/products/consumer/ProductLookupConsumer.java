package org.psint.beyosclothing.modules.products.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.cart.dto.external.ProductDetailsLookupRequest;
import org.psint.beyosclothing.modules.cart.dto.external.ProductDetailsLookupResponse;
import org.psint.beyosclothing.modules.cart.dto.external.ProductLookupRequest;
import org.psint.beyosclothing.modules.cart.dto.external.ProductLookupResponse;
import org.psint.beyosclothing.modules.products.entity.Product;
import org.psint.beyosclothing.modules.products.entity.ProductVariant;
import org.psint.beyosclothing.modules.products.repository.ProductDimensionRepository;
import org.psint.beyosclothing.modules.products.repository.ProductRepository;
import org.psint.beyosclothing.modules.products.repository.ProductVariantRepository;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

/**
 * Product Lookup Consumer
 * Handles cross-module product lookup requests from Cart module
 * TWO TYPES OF LOOKUPS:
 * 1. UUID-based lookup (used when adding items to cart)
 * 2. ID-based lookup (used when displaying cart items)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductLookupConsumer {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductDimensionRepository productDimensionRepository;
    private final ObjectMapper objectMapper;

    /**
     * QUEUE 1: product.lookup.request
     * Purpose: Lookup product by UUID (when adding to cart)
     * Request: ProductLookupRequest (contains productUuid, variantUuid)
     * Response: ProductLookupResponse (returns productId, variantId, prices)
     * Caller: CartServiceImpl.addToCart()
     */
    @RabbitListener(queues = "${app.rabbitmq.queue.product-lookup-request:product.lookup.request}")
    public ProductLookupResponse handleProductLookupRequest(ProductLookupRequest request) {
        log.debug("🔍 [UUID LOOKUP] Received product lookup request - Product UUID: {}, Variant UUID: {}",
                request.getProductUuid(), request.getVariantUuid());

        try {
            Product product = productRepository.findByUuid(request.getProductUuid()).orElse(null);

            if (product == null) {
                log.warn("Product not found: {}", request.getProductUuid());
                return ProductLookupResponse.builder()
                        .requestId(request.getRequestId())
                        .found(false)
                        .errorMessage("Product not found")
                        .build();
            }

            ProductLookupResponse.ProductLookupResponseBuilder responseBuilder = ProductLookupResponse.builder()
                    .requestId(request.getRequestId())
                    .productId(product.getId())
                    .productUuid(product.getUuid())
                    .productTitle(product.getTitle())
                    .productSku(product.getSku())
                    .thumbnailUrl(product.getThumbnailUrl())
                    .showcasePrice(product.getShowcasePrice())
                    .salePrice(product.getSalePrice())
                    .productType(product.getProductType() != null ? product.getProductType().name() : null)
                    .found(true);

            boolean isSimple = product.getProductType() == Product.ProductType.SIMPLE;

            if (!isSimple && request.getVariantUuid() != null && !request.getVariantUuid().isEmpty()) {
                // VARIABLE product — variant UUID explicitly provided
                ProductVariant variant = variantRepository.findByUuid(request.getVariantUuid()).orElse(null);

                if (variant == null || !variant.getProductId().equals(product.getId())) {
                    log.warn("Variant not found or doesn't belong to product: {}", request.getVariantUuid());
                    return ProductLookupResponse.builder()
                            .requestId(request.getRequestId())
                            .found(false)
                            .errorMessage("Variant not found")
                            .build();
                }

                responseBuilder
                        .variantId(variant.getId())
                        .variantUuid(variant.getUuid())
                        .variantSku(variant.getSku())
                        .variantAttributeSummary(variant.getAttributeSummary())
                        .variantShowcasePrice(variant.getShowcasePrice())
                        .variantSalePrice(variant.getSalePrice())
                        .variantResellerPrice(variant.getResellerPrice());

                // Populate wholesale from this variant (only if both fields are present)
                if (variant.getWholesalePrice() != null && variant.getWholesaleMinQty() != null) {
                    responseBuilder
                            .wholesalePrice(variant.getWholesalePrice())
                            .wholesaleMinQty(variant.getWholesaleMinQty());
                    log.debug("Wholesale pricing resolved from VARIABLE variant [{}]: price={}, minQty={}",
                            variant.getUuid(), variant.getWholesalePrice(), variant.getWholesaleMinQty());
                }

            } else if (isSimple && product.getDefaultVariantId() != null) {
                // SIMPLE product — look up the default variant for wholesale info only
                ProductVariant defaultVariant = variantRepository.findById(product.getDefaultVariantId()).orElse(null);

                if (defaultVariant != null && defaultVariant.getWholesalePrice() != null
                        && defaultVariant.getWholesaleMinQty() != null) {
                    responseBuilder
                            .wholesalePrice(defaultVariant.getWholesalePrice())
                            .wholesaleMinQty(defaultVariant.getWholesaleMinQty());
                    log.debug("Wholesale pricing resolved from SIMPLE product default variant [id={}]: price={}, minQty={}",
                            product.getDefaultVariantId(), defaultVariant.getWholesalePrice(), defaultVariant.getWholesaleMinQty());
                }
            }

            ProductLookupResponse response = responseBuilder.build();
            log.debug("Product lookup successful - Product ID: {}, Variant ID: {}, WholesalePrice: {}, WholesaleMinQty: {}",
                    response.getProductId(), response.getVariantId(),
                    response.getWholesalePrice(), response.getWholesaleMinQty());

            return response;

        } catch (Exception e) {
            log.error("Error processing product lookup request", e);
            return ProductLookupResponse.builder()
                    .found(false)
                    .errorMessage("Error: " + e.getMessage())
                    .build();
        }
    }

    /**
     * QUEUE 2: product.details.lookup.request
     * Purpose: Lookup product details by ID (when displaying cart)
     * Request: ProductDetailsLookupRequest (contains productId, variantId)
     * Response: ProductDetailsLookupResponse (returns title, SKU, images, etc.)
     * Caller: CartServiceImpl.buildCartItemResponse()
     */
    @RabbitListener(queues = "${app.rabbitmq.queue.product-details-lookup-request:product.details.lookup.request}")
    public ProductDetailsLookupResponse handleProductDetailsLookupRequest(Message message) {
        log.debug("🔍 [ID LOOKUP] Received product details lookup request message");

        try {
            // Deserialize message to request DTO
            ProductDetailsLookupRequest request = objectMapper.readValue(message.getBody(), ProductDetailsLookupRequest.class);
            log.debug("Deserialized ProductDetailsLookupRequest - Product ID: {}, Variant ID: {}",
                    request.getProductId(), request.getVariantId());

            // Lookup product by ID
            Product product = productRepository.findById(request.getProductId())
                    .orElse(null);

            if (product == null) {
                log.warn("Product not found by ID: {}", request.getProductId());
                return ProductDetailsLookupResponse.builder()
                        .requestId(request.getRequestId())
                        .found(false)
                        .errorMessage("Product not found")
                        .build();
            }

            ProductDetailsLookupResponse.ProductDetailsLookupResponseBuilder responseBuilder =
                ProductDetailsLookupResponse.builder()
                    .requestId(request.getRequestId())
                    .productId(product.getId())
                    .productUuid(product.getUuid())
                    .productTitle(product.getTitle())
                    .productSku(product.getSku())
                    .thumbnailUrl(product.getThumbnailUrl())
                    .productSlug(product.getSlug())
                    .showcasePrice(product.getShowcasePrice())
                    .salePrice(product.getSalePrice())
                    .isOnSale(product.getSalePrice() != null && product.getSalePrice().compareTo(product.getShowcasePrice()) < 0)
                    .isActive(product.getIsActive())
                    .productStatus(product.getIsPublish().toString())
                    .categoryId(product.getCategoryId())  // ✅ Added for category-based promotions
                    .found(true);

            // If variant ID is provided, lookup variant
            if (request.getVariantId() != null) {
                ProductVariant variant = variantRepository.findById(request.getVariantId())
                        .orElse(null);

                if (variant == null || !variant.getProductId().equals(product.getId())) {
                    log.warn("Variant not found by ID or doesn't belong to product: {}", request.getVariantId());
                    return ProductDetailsLookupResponse.builder()
                            .requestId(request.getRequestId())
                            .found(false)
                            .errorMessage("Variant not found")
                            .build();
                }

                responseBuilder
                        .variantId(variant.getId())
                        .variantUuid(variant.getUuid())
                        .variantSku(variant.getSku())
                        .variantAttributeSummary(variant.getAttributeSummary())
                        .variantThumbnailUrl(variant.getThumbnailUrl())
                        .weightKg(variant.getWeightKg())
                        .lengthCm(variant.getLengthCm())
                        .widthCm(variant.getWidthCm())
                        .heightCm(variant.getHeightCm());
            }

            ProductDetailsLookupResponse response = responseBuilder.build();
            log.debug("✅ Product details lookup successful - Product ID: {}, Variant ID: {}",
                    response.getProductId(), response.getVariantId());

            return response;

        } catch (Exception e) {
            log.error("❌ Error processing product details lookup request", e);
            return ProductDetailsLookupResponse.builder()
                    .found(false)
                    .errorMessage("Error: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Helper method to convert response object to JSON Message
     * This ensures the reply is properly serialized as JSON instead of Java serialized object
     */
    private Message createJsonMessage(Object response) {
        try {
            byte[] jsonBody = objectMapper.writeValueAsBytes(response);
            org.springframework.amqp.core.MessageProperties props = new org.springframework.amqp.core.MessageProperties();
            props.setContentType(org.springframework.amqp.core.MessageProperties.CONTENT_TYPE_JSON);
            props.setContentEncoding("UTF-8");
            props.setContentLength(jsonBody.length);
            // CRITICAL: __TypeId__ header required for Jackson2JsonMessageConverter to deserialize the reply
            props.setHeader("__TypeId__", response.getClass().getName());
            return new org.springframework.amqp.core.Message(jsonBody, props);
        } catch (Exception e) {
            log.error("Error converting response to JSON message", e);
            throw new RuntimeException("Failed to serialize response", e);
        }
    }
}
