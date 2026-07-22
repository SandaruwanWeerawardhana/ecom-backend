package org.psint.beyosclothing.modules.pos.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.cart.dto.external.StockCheckRequest;
import org.psint.beyosclothing.modules.cart.dto.external.StockCheckResponse;
import org.psint.beyosclothing.modules.pos.entity.PosProductCacheEntity;
import org.psint.beyosclothing.modules.pos.repository.PosProductCacheRepository;
import org.psint.beyosclothing.modules.pos.service.PosStockValidationService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PosStockValidationServiceImpl implements PosStockValidationService {

    private final RabbitTemplate rabbitTemplate;
    private final PosProductCacheRepository cacheRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.rabbitmq.exchange.inventory}")
    private String inventoryExchange;

    private static final String STOCK_ROUTING_KEY = "inventory.stock.check";
    private static final long RPC_TIMEOUT_MS = 2000L; // 2 seconds

    @Override
    public boolean validateStock(Long productId, Long variantId, int requestedQuantity) {
        log.info("Reach to here");
        try {
            rabbitTemplate.setReplyTimeout(RPC_TIMEOUT_MS);

            StockCheckRequest req = StockCheckRequest.builder()
                    .requestId(java.util.UUID.randomUUID().toString())
                    .productId(productId)
                    .variantId(variantId)
                    .requestedQuantity(requestedQuantity)
                    .build();

            Object rawResponse = rabbitTemplate.convertSendAndReceive(inventoryExchange, STOCK_ROUTING_KEY, req);
            StockCheckResponse resp = convertResponse(rawResponse);
            log.info("Response {}", resp);

            if (resp != null && Boolean.TRUE.equals(resp.getFound())) {
                return Boolean.TRUE.equals(resp.getIsAvailable()) || Boolean.TRUE.equals(resp.getAllowBackorder());
            }

            log.warn("Inventory did not return stock info for productId={}, variantId={} - falling back to cache", productId, variantId);
        } catch (Exception e) {
            log.warn("Inventory RPC failed or timed out for productId={}, variantId={}, requested={} - fallback to cache. Error: {}",
                    productId, variantId, requestedQuantity, e.getMessage());
        }

        // Fallback to cache
        try {
            Optional<PosProductCacheEntity> opt = cacheRepository.findByProductId(productId);
            if (opt.isPresent()) {
                Integer available = opt.get().getStockAvailable();
                int avail = available == null ? 0 : available;
                boolean ok = avail >= requestedQuantity;
                if (!ok) {
                    log.warn("Stock validation failed (cache fallback) productId={}, variantId={}, requested={}, available={}",
                            productId, variantId, requestedQuantity, avail);
                }
                return ok;
            } else {
                log.warn("No cache entry for productId={} when falling back; rejecting request to avoid oversell", productId);
                return false;
            }
        } catch (Exception e) {
            log.error("Error during cache fallback stock validation for productId={}, variantId={}: {}", productId, variantId, e.getMessage());
            return false;
        }
    }

    private StockCheckResponse convertResponse(Object rawResponse) {
        if (rawResponse == null) {
            return null;
        }

        if (rawResponse instanceof StockCheckResponse response) {
            return response;
        }

        if (rawResponse instanceof byte[] bytes) {
            try {
                return objectMapper.readValue(bytes, StockCheckResponse.class);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to deserialize stock RPC byte[] response", e);
            }
        }

        if (rawResponse instanceof String json) {
            try {
                return objectMapper.readValue(json.getBytes(StandardCharsets.UTF_8), StockCheckResponse.class);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to deserialize stock RPC JSON response", e);
            }
        }

        return objectMapper.convertValue(rawResponse, StockCheckResponse.class);
    }
}
