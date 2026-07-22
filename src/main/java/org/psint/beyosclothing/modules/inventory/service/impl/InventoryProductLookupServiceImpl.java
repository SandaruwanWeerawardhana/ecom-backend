package org.psint.beyosclothing.modules.inventory.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.inventory.dto.external.InventoryProductBulkLookupResponse;
import org.psint.beyosclothing.modules.inventory.dto.external.InventoryProductLookupRequest;
import org.psint.beyosclothing.modules.inventory.dto.external.InventoryProductLookupResponse;
import org.psint.beyosclothing.modules.inventory.service.InventoryProductLookupService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sends a request-reply RabbitMQ message to the Product module to obtain
 * the product title and SKU for a given productId / variantId.
 * Flow:
 *   Inventory → [product exchange / inventory.product.lookup.request] → Product module
 *   Product module → reply-to queue → Inventory
 * No Product-module class is imported here; communication is purely via the
 * shared {@link InventoryProductLookupRequest} / {@link InventoryProductLookupResponse}
 * DTOs that live inside the inventory module.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryProductLookupServiceImpl implements InventoryProductLookupService {

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchange.product}")
    private String productExchange;

    private static final String ROUTING_KEY = "inventory.product.lookup.request";
    private static final String BULK_ROUTING_KEY = "inventory.product.bulk.lookup.request";

    @Override
    public InventoryProductLookupResponse lookupProductInfo(Long productId, Long variantId) {
        log.debug("Looking up product info via RabbitMQ - productId: {}, variantId: {}", productId, variantId);

        try {
            String requestId = UUID.randomUUID().toString();

            InventoryProductLookupRequest request = InventoryProductLookupRequest.builder()
                    .requestId(requestId)
                    .productId(productId)
                    .variantId(variantId)
                    .build();

            InventoryProductLookupResponse response = (InventoryProductLookupResponse)
                    rabbitTemplate.convertSendAndReceive(productExchange, ROUTING_KEY, request);

            if (response != null && Boolean.TRUE.equals(response.getFound())) {
                log.debug("Product info received - title: {}, sku: {}", response.getProductTitle(), response.getSku());
                return response;
            }

            log.warn("Product info not found for productId: {}, variantId: {}", productId, variantId);
            return InventoryProductLookupResponse.builder()
                    .requestId(requestId)
                    .found(false)
                    .productTitle("Unknown Product")
                    .productType("SIMPLE")
                    .sku(null)
                    .inventoryStatus("IN_STOCK")
                    .build();

        } catch (Exception e) {
            log.error("Error looking up product info for productId: {}, variantId: {}", productId, variantId, e);
            return InventoryProductLookupResponse.builder()
                    .found(false)
                    .productTitle("Unknown Product")
                    .productType("SIMPLE")
                    .sku(null)
                    .inventoryStatus("IN_STOCK")
                    .errorMessage("RabbitMQ lookup failed: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public Map<String, InventoryProductLookupResponse> lookupProductInfoBulk(
            List<InventoryProductLookupRequest.LookupItem> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            String requestId = UUID.randomUUID().toString();

            InventoryProductLookupRequest request = InventoryProductLookupRequest.builder()
                    .requestId(requestId)
                    .items(items)
                    .build();

            InventoryProductBulkLookupResponse response = (InventoryProductBulkLookupResponse)
                    rabbitTemplate.convertSendAndReceive(productExchange, BULK_ROUTING_KEY, request);

            if (response != null && Boolean.TRUE.equals(response.getSuccess()) && response.getItems() != null) {
                return response.getItems();
            }

            log.warn("Bulk product info not found for {} items", items.size());
            return Collections.emptyMap();
        } catch (Exception e) {
            log.error("Error looking up bulk product info", e);
            return Collections.emptyMap();
        }
    }
}

