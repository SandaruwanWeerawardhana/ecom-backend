package org.psint.beyosclothing.modules.pos.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.pos.dto.document.PosProductDocument;
import org.psint.beyosclothing.modules.pos.entity.PosProductCacheEntity;
import org.psint.beyosclothing.modules.pos.repository.PosProductCacheRepository;
import org.psint.beyosclothing.modules.pos.service.PosProductIndexService;
import org.psint.beyosclothing.modules.products.events.ProductCreatedEvent;
import org.psint.beyosclothing.modules.products.events.ProductUpdatedEvent;
import org.psint.beyosclothing.modules.inventory.consumer.ProductEventConsumer.ProductDeletedEvent;
import org.psint.beyosclothing.modules.pos.service.impl.PosProductSearchCacheService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@RequiredArgsConstructor
@Slf4j
public class PosProductEventConsumer {

    private final PosProductCacheRepository cacheRepository;
    private final PosProductIndexService indexService;
    private final PosProductSearchCacheService cacheService;

    @RabbitListener(queues = "${app.rabbitmq.queue.pos-product-created:pos.product.created.queue}")
    @Transactional("posTransactionManager")
    public void handleProductCreated(ProductCreatedEvent event) {
        log.info("Received PRODUCT_CREATED for productId={}, uuid={}", event.getProductId(), event.getUuid());

        try {
            PosProductCacheEntity entity = PosProductCacheEntity.builder()
                    .productId(event.getProductId())
                    .uuid(event.getUuid())
                    .sku(event.getSku())
                    .title(event.getTitle())
                    .showcasePrice(event.getShowcasePrice())
                    .isActive(true)
                    .thumbnailUrl(null)
                    .stockAvailable(0)
                    .dateCreated(LocalDateTime.now())
                    .dateUpdated(LocalDateTime.now())
                    .build();

            cacheRepository.save(entity);

            PosProductDocument doc = PosProductDocument.builder()
                    .id(String.valueOf(event.getProductId()))
                    .productId(event.getProductId())
                    .uuid(event.getUuid())
                    .sku(event.getSku())
                    .title(event.getTitle())
                    .description(null)
                    .price(event.getShowcasePrice())
                    .salePrice(null)
                    .stockAvailable(0L)
                    .thumbnailUrl(null)
                    .category(null)
                    .tags(null)
                    .isActive(true)
                    .build();

            indexService.indexProduct(doc);

            // Evict any cached searches related to this product name
            cacheService.evictByProductName(event.getTitle());

            log.info("Pos product cache created and indexed: {}", event.getProductId());
        } catch (Exception e) {
            log.error("Error handling PRODUCT_CREATED for product {}", event.getProductId(), e);
        }
    }

    @RabbitListener(queues = "${app.rabbitmq.queue.pos-product-updated:pos.product.updated.queue}")
    @Transactional("posTransactionManager")
    public void handleProductUpdated(ProductUpdatedEvent event) {
        log.info("Received PRODUCT_UPDATED for productId={}, uuid={}", event.getProductId(), event.getUuid());

        try {
            Optional<PosProductCacheEntity> opt = cacheRepository.findByProductId(event.getProductId());
            PosProductCacheEntity entity = opt.orElse(PosProductCacheEntity.builder()
                    .productId(event.getProductId())
                    .uuid(event.getUuid())
                    .dateCreated(LocalDateTime.now())
                    .build());

            entity.setUuid(event.getUuid());
            entity.setSku(event.getSku());
            entity.setTitle(event.getTitle());
            entity.setDateUpdated(LocalDateTime.now());

            cacheRepository.save(entity);

            PosProductDocument.PosProductDocumentBuilder docBuilder = PosProductDocument.builder()
                    .id(String.valueOf(event.getProductId()))
                    .productId(event.getProductId())
                    .uuid(event.getUuid())
                    .sku(event.getSku())
                    .title(event.getTitle())
                    .description(null)
                    .price(event.getNewPrice() != null ? event.getNewPrice() : entity.getShowcasePrice())
                    .salePrice(event.getNewPrice() != null ? event.getNewPrice() : entity.getSalePrice())
                    .thumbnailUrl(entity.getThumbnailUrl())
                    .category(null)
                    .tags(null)
                    .isActive(entity.getIsActive());

            Long stockLong = entity.getStockAvailable() != null ? entity.getStockAvailable().longValue() : 0L;
            docBuilder.stockAvailable(stockLong);

            PosProductDocument doc = docBuilder.build();

            indexService.indexProduct(doc);

            // Evict cache entries related to this product name
            cacheService.evictByProductName(event.getTitle());

            log.info("Pos product cache updated and reindex: {}", event.getProductId());
        } catch (Exception e) {
            log.error("Error handling PRODUCT_UPDATED for product {}", event.getProductId(), e);
        }
    }

    @RabbitListener(queues = "${app.rabbitmq.queue.pos-product-deleted:pos.product.deleted.queue}")
    @Transactional("posTransactionManager")
    public void handleProductDeleted(ProductDeletedEvent event) {
        log.info("Received PRODUCT_DELETED for productId={}, uuid={}", event.getProductId(), event.getUuid());

        try {
            Optional<PosProductCacheEntity> opt = cacheRepository.findByProductId(event.getProductId());
            if (opt.isPresent()) {
                PosProductCacheEntity entity = opt.get();
                entity.setIsActive(false);
                entity.setDateUpdated(LocalDateTime.now());
                cacheRepository.save(entity);
            }

            indexService.deleteProduct(String.valueOf(event.getProductId()));

            // Evict all cached search results for safety
            cacheService.evictAll();

            log.info("Pos product cache marked inactive and deleted from index: {}", event.getProductId());
        } catch (Exception e) {
            log.error("Error handling PRODUCT_DELETED for product {}", event.getProductId(), e);
        }
    }
}
