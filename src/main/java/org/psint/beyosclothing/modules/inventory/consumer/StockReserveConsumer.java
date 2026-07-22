package org.psint.beyosclothing.modules.inventory.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.inventory.service.InventoryService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stock Reserve Consumer
 * Handles stock reservation requests from Reseller/Order modules
 * Reserves stock temporarily before order confirmation
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StockReserveConsumer {

    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "${app.rabbitmq.queue.stock-reserve-request}")
    @Transactional
    public Message handleStockReserveRequest(Message message) {
        byte[] body = message.getBody();
        Map<String, Object> request;
        try {
            request = objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Failed to deserialize stock reserve request", e);
            return createJsonMessage(buildErrorResponse("Deserialization failed"));
        }
        log.info("Received stock reserve request: {}", request);

        try {
            String action = (String) request.get("action");
            String reservationType = (String) request.get("reservationType");

            if (!"RESERVE".equals(action)) {
                log.warn("Unknown action: {}", action);
                return createJsonMessage(buildErrorResponse("Unknown action: " + action));
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) request.get("items");

            if (items == null || items.isEmpty()) {
                log.warn("No items provided for reservation");
                return createJsonMessage(buildErrorResponse("No items provided for reservation"));
            }

            List<Map<String, Object>> failedItems = new ArrayList<>();
            List<Map<String, Object>> successItems = new ArrayList<>();

            // Process each item
            for (Map<String, Object> item : items) {
                Long productId = getLongValue(item.get("productId"));
                Long variantId = getLongValue(item.get("variantId"));
                Integer quantity = getIntegerValue(item.get("quantity"));

                if (productId == null || quantity == null || quantity <= 0) {
                    log.warn("Invalid item data: productId={}, variantId={}, quantity={}",
                            productId, variantId, quantity);
                    Map<String, Object> failedItem = new HashMap<>();
                    failedItem.put("productId", productId);
                    failedItem.put("variantId", variantId);
                    failedItem.put("reason", "Invalid item data");
                    failedItems.add(failedItem);
                    continue;
                }

                try {
                    // Reserve stock using existing service method
                    // For reseller orders, we use a temporary orderId (can be null for pre-reservation)
                    boolean reserved = inventoryService.reserveStock(productId, variantId, quantity, null);

                    if (reserved) {
                        Map<String, Object> successItem = new HashMap<>();
                        successItem.put("productId", productId);
                        successItem.put("variantId", variantId);
                        successItem.put("quantity", quantity);
                        successItems.add(successItem);
                        log.info("Stock reserved - Product ID: {}, Variant ID: {}, Quantity: {}",
                                productId, variantId, quantity);
                    } else {
                        Map<String, Object> failedItem = new HashMap<>();
                        failedItem.put("productId", productId);
                        failedItem.put("variantId", variantId);
                        failedItem.put("reason", "Insufficient stock or backorder not allowed");
                        failedItems.add(failedItem);
                        log.warn("Failed to reserve stock - Product ID: {}, Variant ID: {}",
                                productId, variantId);
                    }
                } catch (Exception e) {
                    log.error("Error reserving stock for Product ID: {}, Variant ID: {}",
                            productId, variantId, e);
                    Map<String, Object> failedItem = new HashMap<>();
                    failedItem.put("productId", productId);
                    failedItem.put("variantId", variantId);
                    failedItem.put("reason", "Error: " + e.getMessage());
                    failedItems.add(failedItem);
                }
            }

            // If any item failed, rollback entire transaction
            if (!failedItems.isEmpty()) {
                log.warn("Stock reservation failed for {} items", failedItems.size());
                return createJsonMessage(buildFailureResponse(failedItems, successItems));
            }

            log.info("Stock reservation successful for {} items", successItems.size());
            return createJsonMessage(buildSuccessResponse(successItems));

        } catch (Exception e) {
            log.error("Error processing stock reserve request", e);
            return createJsonMessage(buildErrorResponse("Error processing request: " + e.getMessage()));
        }
    }

    private Map<String, Object> buildSuccessResponse(List<Map<String, Object>> successItems) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Stock reserved successfully");
        response.put("reservedItems", successItems);
        return response;
    }

    private Map<String, Object> buildFailureResponse(List<Map<String, Object>> failedItems,
                                                      List<Map<String, Object>> successItems) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", "Failed to reserve stock for some items");
        response.put("failedItems", failedItems);
        response.put("successItems", successItems);
        return response;
    }

    private Map<String, Object> buildErrorResponse(String error) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", error);
        return response;
    }

    private Long getLongValue(Object value) {
        if (value == null) return null;
        if (value instanceof Long) return (Long) value;
        if (value instanceof Integer) return ((Integer) value).longValue();
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private Integer getIntegerValue(Object value) {
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Long) return ((Long) value).intValue();
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private Message createJsonMessage(Map<String, Object> response) {
        try {
            byte[] jsonBody = objectMapper.writeValueAsBytes(response);
            MessageProperties props = new MessageProperties();
            props.setContentType("application/json");
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

