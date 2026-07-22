package org.psint.beyosclothing.modules.inventory.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.inventory.dto.external.InventoryProductLookupResponse;
import org.psint.beyosclothing.modules.inventory.dto.request.InventoryUpdateRequest;
import org.psint.beyosclothing.modules.inventory.dto.response.InventoryUpdateResponse;
import org.psint.beyosclothing.modules.inventory.entity.ProductStockEntity;
import org.psint.beyosclothing.modules.inventory.entity.StockMovementLogEntity;
import org.psint.beyosclothing.modules.inventory.events.StockStatusUpdatedEvent;
import org.psint.beyosclothing.modules.inventory.repository.ProductStockRepository;
import org.psint.beyosclothing.modules.inventory.repository.StockMovementLogRepository;
import org.psint.beyosclothing.modules.inventory.service.InventoryProductLookupService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Consumer for inventory stock decrease requests from the Order/POS modules.
 *
 * <p>Uses the raw {@link Message} RPC pattern (see {@code StockCheckConsumer}): the request body
 * is deserialized manually and the reply is built manually with an explicit {@code __TypeId__}
 * header. This avoids the {@code DefaultClassMapper} mismatch that occurs when the sender's
 * {@code __TypeId__} (order module's InventoryUpdateRequest) differs from the listener's type.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryStockDecreaseConsumer {

    private final ProductStockRepository productStockRepository;
    private final StockMovementLogRepository stockMovementLogRepository;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;
    private final InventoryProductLookupService inventoryProductLookupService;

    @Value("${app.rabbitmq.exchange.inventory}")
    private String inventoryExchange;

    private static final int PROCESSED_REQUEST_CACHE_LIMIT = 1000;

    /**
     * Replies already produced, keyed by requestId. Callers retry a stock decrease with the same
     * requestId when the RPC reply is lost or times out; replaying the original reply instead of
     * reprocessing prevents the same sale from decrementing stock twice.
     */
    private final Map<String, InventoryUpdateResponse> processedRequests =
            Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, InventoryUpdateResponse> eldest) {
                    return size() > PROCESSED_REQUEST_CACHE_LIMIT;
                }
            });

    @RabbitListener(queues = "${app.rabbitmq.queue.stock-decrease-request}")
    @Transactional(transactionManager = "inventoryTransactionManager")
    public Message handleStockDecreaseRequest(Message message) {
        // Deserialize the request body manually so the sender's __TypeId__ header is ignored
        InventoryUpdateRequest request;
        try {
            request = objectMapper.readValue(message.getBody(), InventoryUpdateRequest.class);
        } catch (Exception e) {
            log.error("Failed to deserialize InventoryUpdateRequest from message body", e);
            return createJsonMessage(InventoryUpdateResponse.builder()
                    .success(false)
                    .errorMessage("Invalid inventory update request payload")
                    .build());
        }

        List<InventoryUpdateRequest.InventoryItem> items = request.getItems() != null
                ? request.getItems() : List.of();
        log.info("Received stock decrease request - Request ID: {}, Items: {}",
                request.getRequestId(), items.size());

        String requestId = request.getRequestId();
        if (requestId != null && !requestId.isBlank()) {
            InventoryUpdateResponse alreadyProcessed = processedRequests.get(requestId);
            if (alreadyProcessed != null) {
                log.info("Duplicate stock decrease request {} - replaying original reply without reprocessing",
                        requestId);
                return createJsonMessage(alreadyProcessed);
            }
        }

        List<InventoryUpdateResponse.FailedItem> failedItems = new ArrayList<>();

        try {
            for (InventoryUpdateRequest.InventoryItem item : items) {
                try {
                    // Find product stock
                    ProductStockEntity stock = findProductStock(item.getProductId(), item.getVariantId());

                    if (stock == null) {
                        log.warn("Stock not found - Product ID: {}, Variant ID: {}",
                                item.getProductId(), item.getVariantId());
                        failedItems.add(InventoryUpdateResponse.FailedItem.builder()
                                .productId(item.getProductId())
                                .variantId(item.getVariantId())
                                .reason("Stock record not found")
                                .build());
                        continue;
                    }

                    // Check if sufficient stock available
                    if (stock.getStockQuantity() < item.getQuantity()) {
                        log.warn("Insufficient stock - Product ID: {}, Variant ID: {}, Available: {}, Requested: {}",
                                item.getProductId(), item.getVariantId(),
                                stock.getStockQuantity(), item.getQuantity());
                        failedItems.add(InventoryUpdateResponse.FailedItem.builder()
                                .productId(item.getProductId())
                                .variantId(item.getVariantId())
                                .reason("Insufficient stock. Available: " + stock.getStockQuantity() + ", Requested: " + item.getQuantity())
                                .build());
                        continue;
                    }

                    // Decrease stock
                    Integer oldQuantity = stock.getStockQuantity();
                    stock.setStockQuantity(stock.getStockQuantity() - item.getQuantity());
                    stock.setDateUpdated(LocalDateTime.now());
                    productStockRepository.save(stock);

                    // Create stock movement log
                    createStockMovementLog(stock, oldQuantity, stock.getStockQuantity(),
                            "ORDER_PLACED", "Stock decreased due to order placement");

                    log.info("Stock decreased - Product ID: {}, Variant ID: {}, Old: {}, New: {}",
                            item.getProductId(), item.getVariantId(), oldQuantity, stock.getStockQuantity());

                    // Sync product inventory status when the sale depletes the stock
                    if (stock.getStockQuantity() <= 0) {
                        publishOutOfStockStatus(stock);
                    }

                } catch (Exception e) {
                    log.error("Error decreasing stock for Product ID: {}, Variant ID: {}",
                            item.getProductId(), item.getVariantId(), e);
                    failedItems.add(InventoryUpdateResponse.FailedItem.builder()
                            .productId(item.getProductId())
                            .variantId(item.getVariantId())
                            .reason("Error: " + e.getMessage())
                            .build());
                }
            }

            boolean success = failedItems.isEmpty();
            log.info("Stock decrease request processed - Request ID: {}, Success: {}, Failed: {}",
                    requestId, success, failedItems.size());

            InventoryUpdateResponse response = InventoryUpdateResponse.builder()
                    .requestId(requestId)
                    .success(success)
                    .failedItems(failedItems.isEmpty() ? null : failedItems)
                    .build();
            if (requestId != null && !requestId.isBlank()) {
                processedRequests.put(requestId, response);
            }
            return createJsonMessage(response);

        } catch (Exception e) {
            log.error("Error processing stock decrease request - Request ID: {}", requestId, e);
            return createJsonMessage(InventoryUpdateResponse.builder()
                    .requestId(requestId)
                    .success(false)
                    .errorMessage("Error processing stock decrease: " + e.getMessage())
                    .build());
        }
    }

    private ProductStockEntity findProductStock(Long productId, Long variantId) {
        if (variantId != null) {
            return productStockRepository.findByProductIdAndVariantId(productId, variantId)
                    .orElse(null);
        } else {
            return productStockRepository.findByProductIdAndVariantIdIsNull(productId)
                    .orElse(null);
        }
    }

    private void createStockMovementLog(ProductStockEntity stock, Integer oldQuantity,
                                        Integer newQuantity, String movementType, String notes) {
        try {
            StockMovementLogEntity movementLog = StockMovementLogEntity.builder()
                    .productId(stock.getProductId())
                    .variantId(stock.getVariantId())
                    .movementType(StockMovementLogEntity.MovementType.OUT) // OUT because stock is decreasing
                    .quantityChanged(newQuantity - oldQuantity) // Will be negative for decrease
                    .quantityBefore(oldQuantity)
                    .quantityAfter(newQuantity)
                    .referenceType("ORDER") // Reference type for order placement
                    .notes(notes)
                    .isActive(true)
                    .build();
            stockMovementLogRepository.save(movementLog);

            log.debug("Stock movement log created - Product ID: {}, Variant ID: {}, Before: {}, After: {}",
                    stock.getProductId(), stock.getVariantId(), oldQuantity, newQuantity);
        } catch (Exception e) {
            // Log but don't fail the transaction
            log.warn("Failed to create stock movement log", e);
        }
    }

    /**
     * Publishes a {@link StockStatusUpdatedEvent} so the Product module flips inventory_status to
     * OUT_OF_STOCK once a sale depletes the stock, keeping the products table in sync with the
     * product_stock table. Best-effort: a failure here must never fail the stock decrease.
     * Time complexity: O(1) plus one cross-module product lookup.
     */
    private void publishOutOfStockStatus(ProductStockEntity stock) {
        try {
            String productType = null;
            InventoryProductLookupResponse info =
                    inventoryProductLookupService.lookupProductInfo(stock.getProductId(), stock.getVariantId());
            if (info != null && info.getProductType() != null) {
                productType = info.getProductType();
            }

            StockStatusUpdatedEvent event = StockStatusUpdatedEvent.builder()
                    .productId(stock.getProductId())
                    .variantId(stock.getVariantId())
                    .productType(productType)
                    .status("OUT_OF_STOCK")
                    .updatedAt(LocalDateTime.now())
                    .build();

            rabbitTemplate.convertAndSend(inventoryExchange, "inventory.stock.status.update", event);
            log.info("Published OUT_OF_STOCK StockStatusUpdatedEvent - Product ID: {}, Variant ID: {}",
                    stock.getProductId(), stock.getVariantId());
        } catch (Exception e) {
            log.warn("Failed to publish OUT_OF_STOCK status for Product ID {}: {}",
                    stock.getProductId(), e.getMessage());
        }
    }

    /**
     * Converts a response object to a JSON {@link Message} with the {@code __TypeId__} header set
     * so the requester's Jackson2JsonMessageConverter can deserialize the RPC reply.
     */
    private Message createJsonMessage(Object response) {
        try {
            byte[] jsonBody = objectMapper.writeValueAsBytes(response);
            MessageProperties props = new MessageProperties();
            props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            props.setContentEncoding("UTF-8");
            props.setContentLength(jsonBody.length);
            // CRITICAL: __TypeId__ header required for Jackson2JsonMessageConverter to deserialize the reply
            props.setHeader("__TypeId__", response.getClass().getName());
            return new Message(jsonBody, props);
        } catch (Exception e) {
            log.error("Error converting response to JSON message", e);
            throw new RuntimeException("Failed to serialize response", e);
        }
    }
}
