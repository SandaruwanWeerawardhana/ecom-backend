package org.psint.beyosclothing.modules.inventory.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.cart.dto.external.StockCheckRequest;
import org.psint.beyosclothing.modules.cart.dto.external.StockCheckResponse;
import org.psint.beyosclothing.modules.inventory.entity.ProductStockEntity;
import org.psint.beyosclothing.modules.inventory.repository.ProductStockRepository;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Stock Check Consumer
 * Handles cross-module stock availability check requests
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StockCheckConsumer {

    private final ProductStockRepository inventoryStockRepository;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "${app.rabbitmq.queue.stock-check-request:inventory.stock.check}")
    public Message handleStockCheckRequest(Message message) {
        // Get the byte body and deserialize it manually to StockCheckRequest
        byte[] body = message.getBody();

        // Use ObjectMapper to deserialize - inject ObjectMapper as a field
        StockCheckRequest request;
        try {
            request = objectMapper.readValue(body, StockCheckRequest.class);
        } catch (Exception e) {
            log.error("Failed to deserialize StockCheckRequest from message body", e);
            return createJsonMessage(StockCheckResponse.builder()
                    .availableStock(0)
                    .isAvailable(false)
                    .allowBackorder(false)
                    .found(false)
                    .build());
        }

        log.debug("Received stock check request - Product ID: {}, Variant ID: {}, Quantity: {}",
                request.getProductId(), request.getVariantId(), request.getRequestedQuantity());

        try {
            // Validate request
            if (request.getRequestedQuantity() == null) {
                log.warn("Stock check request missing requestedQuantity - Product ID: {}, Variant ID: {}",
                        request.getProductId(), request.getVariantId());
                return createJsonMessage(StockCheckResponse.builder()
                        .requestId(request.getRequestId())
                        .productId(request.getProductId())
                        .variantId(request.getVariantId())
                        .availableStock(0)
                        .isAvailable(false)
                        .allowBackorder(false)
                        .found(false)
                        .build());
            }

            // Find stock record
            ProductStockEntity stock;
            if (request.getVariantId() != null) {
                stock = inventoryStockRepository
                        .findByProductIdAndVariantId(request.getProductId(), request.getVariantId())
                        .orElse(null);
            } else {
                stock = inventoryStockRepository
                        .findByProductIdAndVariantIdIsNull(request.getProductId())
                        .orElse(null);
            }

            if (stock == null) {
                log.warn("Stock record not found - Product ID: {}, Variant ID: {}",
                        request.getProductId(), request.getVariantId());
                return createJsonMessage(StockCheckResponse.builder()
                        .requestId(request.getRequestId())
                        .productId(request.getProductId())
                        .variantId(request.getVariantId())
                        .availableStock(0)
                        .isAvailable(false)
                        .allowBackorder(false)
                        .found(false)
                        .build());
            }

            // Calculate available stock
            Integer availableStock = stock.getStockQuantity();
            Boolean isAvailable = availableStock >= request.getRequestedQuantity();
            Boolean allowBackorder = stock.getAllowBackorder();

            StockCheckResponse response = StockCheckResponse.builder()
                    .requestId(request.getRequestId())
                    .productId(request.getProductId())
                    .variantId(request.getVariantId())
                    .availableStock(availableStock)
                    .isAvailable(isAvailable)
                    .allowBackorder(allowBackorder)
                    .found(true)
                    .build();

            log.debug("Stock check result - Available: {}, Stock: {}, Backorder: {}",
                    isAvailable, availableStock, allowBackorder);

            return createJsonMessage(response);

        } catch (Exception e) {
            log.error("Error processing stock check request", e);
            return createJsonMessage(StockCheckResponse.builder()
                    .requestId(request.getRequestId())
                    .productId(request.getProductId())
                    .variantId(request.getVariantId())
                    .availableStock(0)
                    .isAvailable(false)
                    .allowBackorder(false)
                    .found(false)
                    .build());
        }
    }


    /**
     * Helper method to convert response object to JSON Message with proper content type
     * Pattern copied from ProductLookupConsumer
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
