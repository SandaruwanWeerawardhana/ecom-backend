package org.psint.beyosclothing.modules.pos.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.inventory.consumer.ProductEventConsumer.ProductDeletedEvent;
import org.psint.beyosclothing.modules.pos.dto.document.PosProductDocument;
import org.psint.beyosclothing.modules.pos.entity.PosProductCacheEntity;
import org.psint.beyosclothing.modules.pos.exception.PosIndexingException;
import org.psint.beyosclothing.modules.pos.exception.PosSyncException;
import org.psint.beyosclothing.modules.pos.repository.PosProductCacheRepository;
import org.psint.beyosclothing.modules.pos.service.PosProductIndexService;
import org.psint.beyosclothing.modules.pos.service.PosProductSyncService;
import org.psint.beyosclothing.modules.products.events.ProductCreatedEvent;
import org.psint.beyosclothing.modules.products.events.ProductUpdatedEvent;
import org.springframework.amqp.core.Message;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PosProductSyncServiceImpl implements PosProductSyncService {

    private static final int UUID_MAX_LENGTH = 36;
    private static final int SKU_MAX_LENGTH = 100;
    private static final int TITLE_MAX_LENGTH = 255;
    private static final int THUMBNAIL_MAX_LENGTH = 500;

    private final PosProductCacheRepository cacheRepository;
    private final PosProductIndexService indexService;

    @Override
    @Transactional(
            value = "posTransactionManager",
            propagation = Propagation.REQUIRED,
            isolation = Isolation.READ_COMMITTED,
            rollbackFor = Exception.class
    )
    public void processProductCreated(ProductCreatedEvent event, Message message) {
        Long productId = event.getProductId();
        String messageId = getMessageId(message);
        LocalDateTime syncedAt = LocalDateTime.now();

        log.info("[POS_SYNC] Processing PRODUCT_CREATED - productId={}, uuid={}, messageId={}",
                productId, event.getUuid(), messageId);

        try {
            PosProductCacheEntity cachedEntity = upsertProductCache(event, syncedAt);
            indexProduct(cachedEntity, "index");
            log.info("[POS_SYNC] PRODUCT_CREATED synced - productId={}, cacheId={}", productId, cachedEntity.getId());
        } catch (DataIntegrityViolationException | OptimisticLockingFailureException ex) {
            log.error("[POS_SYNC] PRODUCT_CREATED cache write failed - productId={}, messageId={}", productId, messageId, ex);
            throw new PosSyncException("Failed to sync created product to POS cache, productId=" + productId, ex);
        } catch (Exception ex) {
            log.error("[POS_SYNC] PRODUCT_CREATED sync failed - productId={}, messageId={}", productId, messageId, ex);
            throw new PosSyncException("Failed to process PRODUCT_CREATED event for productId=" + productId, ex);
        }
    }

    @Override
    @Transactional(
            value = "posTransactionManager",
            propagation = Propagation.REQUIRED,
            isolation = Isolation.READ_COMMITTED,
            rollbackFor = Exception.class
    )
    public void processProductUpdated(ProductUpdatedEvent event, Message message) {
        Long productId = event.getProductId();
        String messageId = getMessageId(message);
        LocalDateTime syncedAt = LocalDateTime.now();

        log.info("[POS_SYNC] Processing PRODUCT_UPDATED - productId={}, uuid={}, messageId={}",
                productId, event.getUuid(), messageId);

        try {
            PosProductCacheEntity cachedEntity = updateProductCache(event, syncedAt);
            indexProduct(cachedEntity, "reindex");
            log.info("[POS_SYNC] PRODUCT_UPDATED synced - productId={}, cacheId={}", productId, cachedEntity.getId());
        } catch (Exception ex) {
            log.error("[POS_SYNC] PRODUCT_UPDATED sync failed - productId={}, messageId={}", productId, messageId, ex);
            throw new PosSyncException("Failed to process PRODUCT_UPDATED event for productId=" + productId, ex);
        }
    }

    @Override
    @Transactional(
            value = "posTransactionManager",
            propagation = Propagation.REQUIRED,
            isolation = Isolation.READ_COMMITTED,
            rollbackFor = Exception.class
    )
    public void processProductDeleted(ProductDeletedEvent event, Message message) {
        Long productId = event.getProductId();
        String messageId = getMessageId(message);
        LocalDateTime syncedAt = LocalDateTime.now();

        log.info("[POS_SYNC] Processing PRODUCT_DELETED - productId={}, uuid={}, messageId={}",
                productId, event.getUuid(), messageId);

        try {
            int rows = softDeleteProductCache(productId, syncedAt);
            deleteIndexedProduct(productId);
            log.info("[POS_SYNC] PRODUCT_DELETED synced - productId={}, rows={}", productId, rows);
        } catch (Exception ex) {
            log.error("[POS_SYNC] PRODUCT_DELETED sync failed - productId={}, messageId={}", productId, messageId, ex);
            throw new PosSyncException("Failed to process PRODUCT_DELETED event for productId=" + productId, ex);
        }
    }

    private PosProductCacheEntity upsertProductCache(ProductCreatedEvent event, LocalDateTime syncedAt) {
        PosProductCacheEntity entity = cacheRepository.findFirstByProductIdOrderByIdAsc(event.getProductId())
                .orElseGet(() -> PosProductCacheEntity.builder()
                        .productId(event.getProductId())
                        .dateCreated(syncedAt)
                        .build());

        applyCreatedSnapshot(entity, event, syncedAt);
        return cacheRepository.saveAndFlush(entity);
    }

    private PosProductCacheEntity updateProductCache(ProductUpdatedEvent event, LocalDateTime syncedAt) {
        PosProductCacheEntity entity = cacheRepository.findFirstByProductIdOrderByIdAsc(event.getProductId())
                .orElseGet(() -> PosProductCacheEntity.builder()
                        .productId(event.getProductId())
                        .dateCreated(syncedAt)
                        .build());

        applyUpdatedSnapshot(entity, event, syncedAt);
        return cacheRepository.saveAndFlush(entity);
    }

    private void applyCreatedSnapshot(PosProductCacheEntity entity, ProductCreatedEvent event, LocalDateTime syncedAt) {
        entity.setProductId(event.getProductId());
        entity.setUuid(requiredValue(truncate(event.getUuid(), UUID_MAX_LENGTH), "product uuid", event.getProductId()));
        entity.setSku(truncate(event.getSku(), SKU_MAX_LENGTH));
        entity.setTitle(resolveTitle(event.getTitle(), event.getSku(), event.getUuid(), event.getProductId()));
        entity.setShowcasePrice(resolvePrice(event.getShowcasePrice(), event.getSalePrice()));
        entity.setSalePrice(event.getSalePrice());
        entity.setStockAvailable(resolveStock(event.getStockAvailable(), event.getInitialStockQuantity()));
        entity.setThumbnailUrl(truncate(event.getThumbnailUrl(), THUMBNAIL_MAX_LENGTH));
        entity.setIsActive(true);
        entity.setHasVariants(Boolean.TRUE.equals(event.getHasVariants()));
        entity.setSyncedAt(syncedAt);
        entity.setDateUpdated(syncedAt);
        if (entity.getDateCreated() == null) {
            entity.setDateCreated(syncedAt);
        }
    }

    private void applyUpdatedSnapshot(PosProductCacheEntity entity, ProductUpdatedEvent event, LocalDateTime syncedAt) {
        entity.setProductId(event.getProductId());
        entity.setUuid(requiredValue(truncate(event.getUuid(), UUID_MAX_LENGTH), "product uuid", event.getProductId()));
        entity.setSku(truncate(event.getSku(), SKU_MAX_LENGTH));
        entity.setTitle(resolveTitle(event.getTitle(), event.getSku(), event.getUuid(), event.getProductId()));
        entity.setShowcasePrice(resolvePrice(event.getNewPrice(), entity.getShowcasePrice(), event.getSalePrice()));
        entity.setSalePrice(event.getSalePrice());
        entity.setStockAvailable(resolveStock(event.getStockAvailable(), entity.getStockAvailable()));
        entity.setThumbnailUrl(truncate(event.getThumbnailUrl(), THUMBNAIL_MAX_LENGTH));
        entity.setIsActive(true);
        if (entity.getHasVariants() == null) {
            entity.setHasVariants(false);
        }
        entity.setSyncedAt(syncedAt);
        entity.setDateUpdated(syncedAt);
        if (entity.getDateCreated() == null) {
            entity.setDateCreated(syncedAt);
        }
    }

    private int softDeleteProductCache(Long productId, LocalDateTime syncedAt) {
        List<PosProductCacheEntity> entities = cacheRepository.findAllByProductId(productId);
        if (entities.isEmpty()) {
            log.warn("[POS_SYNC] No POS cache rows found for delete - productId={}", productId);
            return 0;
        }

        for (PosProductCacheEntity entity : entities) {
            entity.setIsActive(false);
            entity.setDateUpdated(syncedAt);
            entity.setSyncedAt(syncedAt);
        }
        cacheRepository.saveAllAndFlush(entities);
        return entities.size();
    }

    private void indexProduct(PosProductCacheEntity entity, String action) {
        try {
            indexService.indexProduct(buildElasticsearchDocument(entity));
        } catch (Exception ex) {
            PosIndexingException indexingException = new PosIndexingException(
                    "Failed to " + action + " POS product " + entity.getProductId(), ex);
            log.error("[POS_SYNC] {}", indexingException.getMessage(), indexingException);
        }
    }

    private void deleteIndexedProduct(Long productId) {
        try {
            indexService.deleteProduct(String.valueOf(productId));
        } catch (Exception ex) {
            log.error("[POS_SYNC] Elasticsearch deletion failed for productId={}", productId, ex);
        }
    }

    private PosProductDocument buildElasticsearchDocument(PosProductCacheEntity entity) {
        return PosProductDocument.builder()
                .id(String.valueOf(entity.getProductId()))
                .productId(entity.getProductId())
                .uuid(entity.getUuid())
                .sku(entity.getSku())
                .title(entity.getTitle())
                .description(null)
                .price(entity.getShowcasePrice())
                .salePrice(entity.getSalePrice())
                .stockAvailable(entity.getStockAvailable() != null ? entity.getStockAvailable().longValue() : 0L)
                .thumbnailUrl(entity.getThumbnailUrl())
                .category(null)
                .tags(null)
                .isActive(entity.getIsActive())
                .build();
    }

    private String resolveTitle(String title, String sku, String uuid, Long productId) {
        String resolved = firstNonBlank(title, sku, uuid, "Product " + productId);
        return truncate(resolved, TITLE_MAX_LENGTH);
    }

    private BigDecimal resolvePrice(BigDecimal... prices) {
        if (prices != null) {
            for (BigDecimal price : prices) {
                if (price != null) {
                    return price;
                }
            }
        }
        return BigDecimal.ZERO;
    }

    private Integer resolveStock(Integer preferred, Integer fallback) {
        if (preferred != null) {
            return preferred;
        }
        if (fallback != null) {
            return fallback;
        }
        return 0;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String requiredValue(String value, String fieldName, Long productId) {
        if (value == null || value.isBlank()) {
            throw new PosSyncException("Missing " + fieldName + " for productId=" + productId);
        }
        return value;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String getMessageId(Message message) {
        if (message == null || message.getMessageProperties() == null) {
            return "DIRECT_SYNC";
        }
        String messageId = message.getMessageProperties().getMessageId();
        return messageId != null ? messageId : "NO_MESSAGE_ID";
    }
}
