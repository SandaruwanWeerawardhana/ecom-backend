package org.psint.beyosclothing.modules.products.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.cart.dto.external.StockCheckRequest;
import org.psint.beyosclothing.modules.cart.dto.external.StockCheckResponse;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * InventoryStockService
 * Fetches product variant stock quantity from beyos_inventory_db using RabbitMQ RPC
 * Database: beyos_inventory_db
 * Table: product_stock
 *
 * FIXED: Proper RPC pattern with:
 * - ReplyTo queue configuration
 * - Correlation ID for request matching
 * - Proper timeout handling
 * - Response validation
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryStockService {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.rabbitmq.exchange.inventory:inventory.exchange}")
    private String inventoryExchange;

    @Value("${app.rabbitmq.queue.stock-check-request:inventory.stock.check}")
    private String stockCheckRequestQueue;

    /**
     * IMPORTANT: This is the ROUTING KEY used in RabbitMQ binding:
     * RabbitMQConfig.stockCheckRequestBinding() -> with("inventory.stock.check")
     *
     * Do NOT confuse routing key with queue name. In most environments they are the same,
     * but if queue name is customized, routing key must still match the binding.
     */
    private static final String STOCK_CHECK_ROUTING_KEY = "inventory.stock.check";

    /**
     * Fetch stock quantity for a product variant from inventory database via RabbitMQ RPC
     *
     * @param productId Product ID from beyos_product_db
     * @param variantId Variant ID from beyos_product_db (null for simple products)
     * @return Stock quantity from product_stock table, or 0 if not found
     */
    public Integer getVariantStockQuantity(Long productId, Long variantId) {
        try {
            // Validate inputs
            if (productId == null) {
                log.warn(" ProductId cannot be null");
                return 0;
            }

            // Build RPC request using the shared stock-check DTO so Spring AMQP can
            // serialize/deserialize it consistently across modules.
            StockCheckRequest request = StockCheckRequest.builder()
                    .requestId(UUID.randomUUID().toString())
                    .productId(productId)
                    .variantId(variantId)
                    .requestedQuantity(1)
                    .build();
            log.info("STEP 2: ✓ Request built (productId={}, variantId={})", productId, variantId);
            log.debug("STEP 3: Request payload: {}", request);

            // Send synchronous RPC request
            log.info("STEP 4: Sending RPC request via exchange='{}', routingKey='{}'",
                inventoryExchange, STOCK_CHECK_ROUTING_KEY);

            long startTime = System.currentTimeMillis();
            Object response = rabbitTemplate.convertSendAndReceive(
                inventoryExchange,
                // routing key MUST match binding key (by default the same as queue name in this project)
                STOCK_CHECK_ROUTING_KEY,
                request
            );
            long duration = System.currentTimeMillis() - startTime;
            log.info("STEP 5: RPC call completed in {} ms", duration);

            if (response == null) {
                log.warn("️STEP 6: ⚠️  NO RESPONSE RECEIVED (timeout or no consumer)");
                log.warn("    Possible causes:");
                log.warn("    1. StockCheckConsumer is NOT running in inventory module");
                log.warn("    2. Queue '{}' doesn't exist", stockCheckRequestQueue);
                log.warn("    3. Exchange '{}' not bound to queue", inventoryExchange);
                log.warn("    4. RabbitMQ connection issue");
                log.warn("    5. Timeout exceeded (5 seconds)");
                return 0;
            }

            // Deserialize / normalize response
            if (response instanceof StockCheckResponse stockResponse) {
                if (Boolean.FALSE.equals(stockResponse.getFound())) {
                    log.warn("STEP 8: Stock record NOT FOUND in inventory database");
                    return 0;
                }

                Integer stock = stockResponse.getAvailableStock();
                if (stock == null) {
                    log.warn("Response missing availableStock field");
                    return 0;
                }

                log.info("SUCCESS - Stock quantity: {} for productId={}, variantId={}",
                        stock, productId, variantId);
                return stock;
            }

            final Map<String, Object> responseMap;
            if (response instanceof Map) {
                // When using Jackson2JsonMessageConverter, the response is usually a LinkedHashMap
                responseMap = (Map<String, Object>) response;
            } else if (response instanceof byte[]) {
                String responseStr = new String((byte[]) response);
                log.debug("STEP 7: Response received (byte[]): {}", responseStr);
                responseMap = objectMapper.readValue(responseStr, new TypeReference<Map<String, Object>>() {});
            } else if (response instanceof String) {
                log.debug("STEP 7: Response received (String): {}", response);
                responseMap = objectMapper.readValue((String) response, new TypeReference<Map<String, Object>>() {});
            } else {
                // Last resort: try to serialize as JSON then read
                String responseStr = objectMapper.writeValueAsString(response);
                log.debug("STEP 7: Response received (other) serialized: {}", responseStr);
                responseMap = objectMapper.readValue(responseStr, new TypeReference<Map<String, Object>>() {});
            }

            log.debug("STEP 7b: Response map: {}", responseMap);

            // Extract values
            Boolean found = (Boolean) responseMap.get("found");
            Boolean success = (Boolean) responseMap.get("success");
            Object stockObj = responseMap.containsKey("availableStock")
                    ? responseMap.get("availableStock")
                    : responseMap.get("stockQuantity");
            String error = (String) responseMap.get("error");

            log.info("STEP 8: Response details - success={}, found={}, error={}", success, found, error);

            if (Boolean.FALSE.equals(found) || Boolean.FALSE.equals(success)) {
                log.warn(" Response indicated failure or missing stock record");
                if (error != null) {
                    log.warn("    Error: {}", error);
                }
                return 0;
            }

            // Extract stock quantity
            if (stockObj == null) {
                log.warn("Response missing availableStock/stockQuantity field");
                return 0;
            }

            Integer stock = ((Number) stockObj).intValue();
            log.info("SUCCESS - Stock quantity: {} for productId={}, variantId={}",
                stock, productId, variantId);

            return stock;

        } catch (Exception e) {
            log.error("EXCEPTION: Error fetching stock from inventory module", e);
            log.error("   ProductId={}, VariantId={}, Error: {}", productId, variantId, e.getMessage());
            return 0;
        }
    }

    /**
     * Get stock quantity for a simple product (no variant)
     *
     * @param productId Product ID
     * @return Stock quantity
     */
    public Integer getProductStockQuantity(Long productId) {
        return getVariantStockQuantity(productId, null);
    }
}

