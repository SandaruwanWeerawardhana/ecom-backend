package org.psint.beyosclothing.modules.products.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.products.entity.*;
import org.psint.beyosclothing.modules.products.repository.*;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Reseller Product Detail Lookup Consumer (Product module)
 * Handles Map-based RPC requests from Reseller module — no cross-module DTO dependency.
 *
 * Full detail lookup:
 *   Request : { requestId, productUuid }
 *   Response: { requestId, found, uuid, title, variants, ... }
 *
 * Variant thumbnail lookup:
 *   Request : { requestId, requestType="VARIANT_THUMBNAIL", variantUuid }
 *   Response: { requestId, found, thumbnailUrl }
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ResellerProductDetailLookupConsumer {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductGalleryRepository galleryRepository;
    private final ProductMetaRepository metaRepository;
    private final ProductDimensionRepository dimensionRepository;
    private final ProductCategoryRepository categoryRepository;
    private final ProductTagRepository tagRepository;
    private final ProductTagMapRepository tagMapRepository;
    private final ProductAttributeRepository attributeRepository;
    private final ProductAttributeValueRepository attributeValueRepository;
    private final VariantAttributeValueRepository variantAttributeValueRepository;
    private final VariantGalleryRepository variantGalleryRepository;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = "${app.rabbitmq.queue.reseller-product-detail-lookup-request:reseller.product.detail.lookup.request}")
    @SendTo
    public Message handleResellerProductDetailLookup(Message message) {
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> request = null;
        String requestId = null;

        try {
            // Deserialize the message body from bytes to Map
            request = objectMapper.readValue(message.getBody(), Map.class);
            requestId = request != null ? (String) request.get("requestId") : null;
            String requestType = request != null ? (String) request.get("requestType") : null;

            log.debug("🔍 [RESELLER PRODUCT LOOKUP] requestId={}, requestType={}", requestId, requestType);

            if ("VARIANT_THUMBNAIL".equals(requestType)) {
                response = handleVariantThumbnailLookup(requestId, request);
            } else {
                response = handleProductDetailLookup(requestId, request);
            }
        } catch (Exception e) {
            log.error("❌ [RESELLER PRODUCT LOOKUP] Error deserializing message: requestId={}", requestId, e);
            response.put("requestId", requestId);
            response.put("found", false);
            response.put("error", "Message deserialization failed: " + e.getMessage());
        }

        // Convert response to JSON bytes
        try {
            byte[] responseBody = objectMapper.writeValueAsBytes(response);
            Message responseMessage = new Message(responseBody, new MessageProperties());
            responseMessage.getMessageProperties().setContentType("application/json");
            responseMessage.getMessageProperties().setContentEncoding("UTF-8");
            return responseMessage;
        } catch (Exception e) {
            log.error("❌ [RESELLER PRODUCT LOOKUP] Error serializing response: requestId={}", requestId, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("requestId", requestId);
            errorResponse.put("found", false);
            errorResponse.put("error", "Response serialization failed: " + e.getMessage());
            try {
                byte[] errorBody = objectMapper.writeValueAsBytes(errorResponse);
                Message errorMessage = new Message(errorBody, new MessageProperties());
                errorMessage.getMessageProperties().setContentType("application/json");
                errorMessage.getMessageProperties().setContentEncoding("UTF-8");
                return errorMessage;
            } catch (Exception ex) {
                log.error("❌ [RESELLER PRODUCT LOOKUP] Fatal error serializing error response", ex);
                throw new RuntimeException("Fatal serialization error", ex);
            }
        }
    }

    // ── Full Product Detail Lookup ────────────────────────────────────────

    private Map<String, Object> handleProductDetailLookup(String requestId, Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        response.put("requestId", requestId);

        try {
            String productUuid = request != null ? (String) request.get("productUuid") : null;

            if (productUuid == null || productUuid.trim().isEmpty()) {
                log.warn("[RESELLER PRODUCT LOOKUP] Missing productUuid requestId={}", requestId);
                response.put("found", false);
                response.put("error", "productUuid is required");
                return response;
            }

            Product product = productRepository.findByUuid(productUuid).orElse(null);
            if (product == null) {
                log.warn("[RESELLER PRODUCT LOOKUP] Product not found UUID={}", productUuid);
                response.put("found", false);
                return response;
            }

            response.put("found", true);
            response.put("uuid", product.getUuid());
            response.put("sku", product.getSku());
            response.put("title", product.getTitle());
            response.put("slug", product.getSlug());
            response.put("shortDescription", product.getShortDescription());
            response.put("description", product.getDescription());
            response.put("thumbnailUrl", product.getThumbnailUrl());
            response.put("showcasePrice", product.getShowcasePrice() != null ? product.getShowcasePrice().toString() : null);
            response.put("salePrice", product.getSalePrice() != null ? product.getSalePrice().toString() : null);
//            response.put("resellerPrice", product.getResellerPrice() != null ? product.getResellerPrice().toString() : null);
            response.put("saleStart", product.getSaleStart() != null ? product.getSaleStart().toString() : null);
            response.put("saleEnd", product.getSaleEnd() != null ? product.getSaleEnd().toString() : null);
            response.put("productType", product.getProductType() != null ? product.getProductType().name() : null);
            response.put("featured", product.getFeatured());
            response.put("visibility", product.getVisibility() != null ? product.getVisibility().name() : null);
            response.put("soldIndividually", product.getSoldIndividually());
            response.put("pricingMode", product.getPricingMode() != null ? product.getPricingMode().name() : null);
            response.put("wholesalePrice", product.getWholesalePrice() != null ? product.getWholesalePrice().toString() : null);
            response.put("wholesaleMinQty", product.getWholesaleMinQty());
            response.put("productionCost", product.getProductionCost() != null ? product.getProductionCost().toString() : null);
            response.put("isPublish", product.getIsPublish());
            response.put("isActive", product.getIsActive());
            response.put("defaultVariantId", product.getDefaultVariantId());
            response.put("dateCreated", product.getDateCreated() != null ? product.getDateCreated().toString() : null);
            response.put("dateUpdated", product.getDateUpdated() != null ? product.getDateUpdated().toString() : null);

            response.put("category", buildCategoryMap(product.getCategoryId()));
            response.put("galleryImages", buildGalleryList(product.getId()));
            response.put("meta", buildMetaMap(product.getId()));
            response.put("dimension", buildDimensionMap(product.getId()));
            response.put("tags", buildTagList(product.getId()));

            List<Map<String, Object>> variantList = buildVariantList(product);
            response.put("variants", variantList);

            // Resolve default variant from the variant list
            Map<String, Object> defaultVariant = null;
            if (product.getDefaultVariantId() != null) {
                defaultVariant = variantList.stream()
                        .filter(v -> product.getDefaultVariantId().equals(v.get("_id")))
                        .findFirst()
                        .orElse(null);
            }
            // Strip the internal _id before sending out
            variantList.forEach(v -> v.remove("_id"));
            if (defaultVariant != null) defaultVariant.remove("_id");
            response.put("defaultVariant", defaultVariant);

            response.put("variantAttributesMap", buildVariantAttributesMap(product.getId()));
            response.put("variantAttributesListMap", buildVariantAttributesListMap(product.getId()));

            log.info("✅ [RESELLER PRODUCT LOOKUP] Found product UUID={}", productUuid);
        } catch (Exception e) {
            log.error("❌ [RESELLER PRODUCT LOOKUP] Error requestId={}", requestId, e);
            response.put("found", false);
            response.put("error", "Internal error: " + e.getMessage());
        }
        return response;
    }

    // ── Variant Thumbnail Lookup ──────────────────────────────────────────

    private Map<String, Object> handleVariantThumbnailLookup(String requestId, Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        response.put("requestId", requestId);

        try {
            String variantUuid = request != null ? (String) request.get("variantUuid") : null;
            if (variantUuid == null || variantUuid.trim().isEmpty()) {
                response.put("found", false);
                response.put("error", "variantUuid is required");
                return response;
            }

            ProductVariant variant = variantRepository.findByUuid(variantUuid).orElse(null);
            if (variant == null) {
                response.put("found", false);
                return response;
            }

            response.put("found", true);
            response.put("thumbnailUrl", variant.getThumbnailUrl());
            log.info("✅ [RESELLER VARIANT THUMBNAIL] variantUuid={}", variantUuid);
        } catch (Exception e) {
            log.error("❌ [RESELLER VARIANT THUMBNAIL] Error requestId={}", requestId, e);
            response.put("found", false);
            response.put("error", "Internal error: " + e.getMessage());
        }
        return response;
    }

    // ── Builder Helpers ───────────────────────────────────────────────────

    private Map<String, Object> buildCategoryMap(Long categoryId) {
        if (categoryId == null) return null;
        ProductCategory category = categoryRepository.findById(categoryId).orElse(null);
        if (category == null) return null;

        String parentName = null;
        String parentUuid = null;
        if (category.getParentId() != null) {
            ProductCategory parent = categoryRepository.findById(category.getParentId()).orElse(null);
            if (parent != null) { parentName = parent.getName(); parentUuid = parent.getUuid(); }
        }

        List<String> subCategoryUuids = categoryRepository.findAllByParentId(category.getId())
                .stream().map(ProductCategory::getUuid).collect(Collectors.toList());

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("uuid", category.getUuid());
        map.put("name", category.getName());
        map.put("slug", category.getSlug());
        map.put("isActive", category.getIsActive());
        map.put("parentCategoryName", parentName);
        map.put("parentCategoryUuid", parentUuid);
        map.put("subCategories", subCategoryUuids);
        map.put("dateCreated", category.getDateCreated() != null ? category.getDateCreated().toString() : null);
        map.put("dateUpdated", category.getDateUpdated() != null ? category.getDateUpdated().toString() : null);
        return map;
    }

    private List<Map<String, Object>> buildGalleryList(Long productId) {
        return galleryRepository.findByProductIdAndIsActiveTrueOrderBySortOrderAsc(productId).stream()
                .map(g -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("uuid", g.getUuid());
                    m.put("mediaUrl", g.getMediaName());
                    m.put("sortOrder", g.getSortOrder());
                    m.put("altText", g.getAltText());
                    m.put("mimeType", g.getMimeType());
                    return m;
                }).collect(Collectors.toList());
    }

    private Map<String, Object> buildMetaMap(Long productId) {
        return metaRepository.findByProductId(productId).map(m -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("uuid", m.getUuid());
            map.put("metaTitle", m.getMetaTitle());
            map.put("metaDescription", m.getMetaDescription());
            map.put("canonicalUrl", m.getCanonicalUrl());
            map.put("metaKeywords", m.getMetaKeywords());
            map.put("ogTitle", m.getOgTitle());
            map.put("ogDescription", m.getOgDescription());
            map.put("ogImage", m.getOgImage());
            map.put("twitterCard", m.getTwitterCard());
            map.put("jsonLd", m.getJsonLd());
            map.put("robotIndex", m.getRobotIndex());
            return map;
        }).orElse(null);
    }

    private Map<String, Object> buildDimensionMap(Long productId) {
        return dimensionRepository.findByProductId(productId).map(d -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("uuid", d.getUuid());
            map.put("weightKg", d.getWeightKg() != null ? d.getWeightKg().toString() : null);
            map.put("lengthCm", d.getLengthCm() != null ? d.getLengthCm().toString() : null);
            map.put("widthCm", d.getWidthCm() != null ? d.getWidthCm().toString() : null);
            map.put("heightCm", d.getHeightCm() != null ? d.getHeightCm().toString() : null);
            return map;
        }).orElse(null);
    }

    private List<Map<String, Object>> buildTagList(Long productId) {
        return tagMapRepository.findByProductId(productId).stream()
                .map(tm -> tagRepository.findById(tm.getTagId()).orElse(null))
                .filter(Objects::nonNull)
                .map(tag -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("uuid", tag.getUuid());
                    m.put("name", tag.getName());
                    m.put("slug", tag.getSlug());
                    m.put("description", tag.getDescription() != null ? tag.getDescription() : null);
                    return m;
                }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> buildVariantList(Product product) {
        return variantRepository.findByProductId(product.getId()).stream()
                .map(v -> buildVariantMap(v))
                .collect(Collectors.toList());
    }

    private Map<String, Object> buildVariantMap(ProductVariant v) {
        Map<String, Object> m = new LinkedHashMap<>();
        // _id is internal — used to resolve default variant; removed before sending
        m.put("_id", v.getId());
        m.put("uuid", v.getUuid());
        m.put("sku", v.getSku());
        m.put("attributeSummary", v.getAttributeSummary());
        m.put("weightKg", v.getWeightKg() != null ? v.getWeightKg().toString() : null);
        m.put("lengthCm", v.getLengthCm() != null ? v.getLengthCm().toString() : null);
        m.put("widthCm", v.getWidthCm() != null ? v.getWidthCm().toString() : null);
        m.put("heightCm", v.getHeightCm() != null ? v.getHeightCm().toString() : null);
        m.put("thumbnailUrl", v.getThumbnailUrl());
        m.put("showcasePrice", v.getShowcasePrice() != null ? v.getShowcasePrice().toString() : null);
        m.put("salePrice", v.getSalePrice() != null ? v.getSalePrice().toString() : null);
        m.put("saleStart", v.getSaleStart() != null ? v.getSaleStart().toString() : null);
        m.put("saleEnd", v.getSaleEnd() != null ? v.getSaleEnd().toString() : null);
        m.put("resellerPrice", v.getResellerPrice() != null ? v.getResellerPrice().toString() : null);
        m.put("wholesalePrice", v.getWholesalePrice() != null ? v.getWholesalePrice().toString() : null);
        m.put("wholesaleMinQty", v.getWholesaleMinQty());
        m.put("productionCost", v.getProductionCost() != null ? v.getProductionCost().toString() : null);
        m.put("isActive", v.getIsActive());
        m.put("dateCreated", v.getDateCreated() != null ? v.getDateCreated().toString() : null);
        m.put("dateUpdated", v.getDateUpdated() != null ? v.getDateUpdated().toString() : null);

        // Attribute values
        List<Map<String, Object>> attrValues = variantAttributeValueRepository.findByVariantId(v.getId()).stream()
                .map(vav -> attributeValueRepository.findById(vav.getAttributeValueId()).orElse(null))
                .filter(Objects::nonNull)
                .map(av -> {
                    Map<String, Object> avMap = new LinkedHashMap<>();
                    avMap.put("uuid", av.getUuid());
                    avMap.put("value", av.getValue());
                    avMap.put("isActive", av.getIsActive());
                    return avMap;
                }).collect(Collectors.toList());
        m.put("attributeValues", attrValues);

        // Gallery
        List<Map<String, Object>> gallery = variantGalleryRepository.findByVariantIdOrderBySortOrder(v.getId()).stream()
                .map(g -> {
                    Map<String, Object> gMap = new LinkedHashMap<>();
                    gMap.put("uuid", g.getUuid());
                    gMap.put("mediaUrl", g.getMediaName());
                    gMap.put("sortOrder", g.getSortOrder());
                    gMap.put("altText", g.getAltText());
                    gMap.put("mimeType", g.getMimeType());
                    return gMap;
                }).collect(Collectors.toList());
        m.put("galleryImages", gallery);

        return m;
    }

    private Map<String, Map<String, String>> buildVariantAttributesMap(Long productId) {
        List<ProductVariant> variants = variantRepository.findByProductIdAndIsActive(productId, true);
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        for (ProductVariant variant : variants) {
            Map<String, String> attrMap = new LinkedHashMap<>();
            variantAttributeValueRepository.findByVariantId(variant.getId()).forEach(vav -> {
                ProductAttributeValue av = attributeValueRepository.findById(vav.getAttributeValueId()).orElse(null);
                if (av != null) {
                    ProductAttribute attr = attributeRepository.findById(av.getAttributeId()).orElse(null);
                    if (attr != null && !attrMap.containsKey(attr.getName())) {
                        attrMap.put(attr.getName(), av.getValue());
                    }
                }
            });
            result.put(variant.getUuid(), attrMap);
        }
        return result;
    }

    private Map<String, Map<String, List<String>>> buildVariantAttributesListMap(Long productId) {
        List<ProductVariant> variants = variantRepository.findByProductIdAndIsActive(productId, true);
        Map<String, Map<String, List<String>>> result = new LinkedHashMap<>();
        for (ProductVariant variant : variants) {
            Map<String, List<String>> attrMap = new LinkedHashMap<>();
            variantAttributeValueRepository.findByVariantId(variant.getId()).forEach(vav -> {
                ProductAttributeValue av = attributeValueRepository.findById(vav.getAttributeValueId()).orElse(null);
                if (av != null) {
                    ProductAttribute attr = attributeRepository.findById(av.getAttributeId()).orElse(null);
                    if (attr != null) {
                        attrMap.computeIfAbsent(attr.getName(), k -> new ArrayList<>()).add(av.getValue());
                    }
                }
            });
            result.put(variant.getUuid(), attrMap);
        }
        return result;
    }
}

