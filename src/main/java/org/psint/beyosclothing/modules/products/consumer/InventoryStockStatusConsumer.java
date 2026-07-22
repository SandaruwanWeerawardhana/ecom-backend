package org.psint.beyosclothing.modules.products.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.inventory.events.StockStatusUpdatedEvent;
import org.psint.beyosclothing.modules.products.entity.Product;
import org.psint.beyosclothing.modules.products.entity.ProductVariant;
import org.psint.beyosclothing.modules.products.repository.ProductRepository;
import org.psint.beyosclothing.modules.products.repository.ProductVariantRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Consumes {@link StockStatusUpdatedEvent} published by the Inventory module
 * when a stock record's inventory status is changed.
 *
 * <p>Routing key : {@code inventory.stock.status.update}
 * <p>Flow:
 * <pre>
 *   Inventory module (update status + publish) ──► this consumer ──► SIMPLE  → Product.inventoryStatus + all ProductVariant.inventoryStatus
 *                                                                  └─► VARIABLE → ProductVariant.inventoryStatus only
 * </pre>
 *
 * Status mapping: IN_STOCK | OUT_OF_STOCK | ON_BACKORDER
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryStockStatusConsumer {

    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;

    @RabbitListener(queues = "${app.rabbitmq.queue.stock-status-update:inventory.stock.status.update.queue}")
    public void handleStockStatusUpdated(StockStatusUpdatedEvent event) {
        log.info("Received StockStatusUpdatedEvent → productId: {}, variantId: {}, productType: {}, status: {}",
                event.getProductId(), event.getVariantId(), event.getProductType(), event.getStatus());

        try {
            if ("SIMPLE".equalsIgnoreCase(event.getProductType())) {
                // ── SIMPLE product: update Product table + all ProductVariant rows ────────
                Product.InventoryStatus productStatus =
                        Product.InventoryStatus.valueOf(event.getStatus());

                productRepository.findById(event.getProductId()).ifPresentOrElse(
                        product -> {
                            product.setInventoryStatus(productStatus);
                            productRepository.save(product);
                            log.info("Updated inventory_status (SIMPLE product) → productId: {}, status: {}",
                                    event.getProductId(), productStatus);
                        },
                        () -> log.warn("Product not found for productId: {}", event.getProductId())
                );

                List<ProductVariant> variants = variantRepository.findByProductId(event.getProductId());
                if (!variants.isEmpty()) {
                    ProductVariant.InventoryStatus variantStatus =
                            ProductVariant.InventoryStatus.valueOf(event.getStatus());
                    variants.forEach(v -> v.setInventoryStatus(variantStatus));
                    variantRepository.saveAll(variants);
                    log.info("Updated inventory_status for {} variant(s) of SIMPLE productId: {}, status: {}",
                            variants.size(), event.getProductId(), variantStatus);
                }
            } else {
                // ── VARIABLE product (or fallback): update ProductVariant table only ──────
                ProductVariant.InventoryStatus variantStatus =
                        ProductVariant.InventoryStatus.valueOf(event.getStatus());

                if (event.getVariantId() != null) {
                    // Update specific variant
                    variantRepository.findById(event.getVariantId()).ifPresentOrElse(
                            variant -> {
                                variant.setInventoryStatus(variantStatus);
                                variantRepository.save(variant);
                                log.info("Updated inventory_status (VARIABLE variant) → variantId: {}, status: {}",
                                        event.getVariantId(), variantStatus);
                            },
                            () -> log.warn("Variant not found for variantId: {}", event.getVariantId())
                    );
                } else {
                    // Update all variants of the product
                    List<ProductVariant> variants = variantRepository.findByProductId(event.getProductId());
                    if (!variants.isEmpty()) {
                        variants.forEach(v -> v.setInventoryStatus(variantStatus));
                        variantRepository.saveAll(variants);
                        log.info("Updated inventory_status for all {} variant(s) of VARIABLE productId: {}, status: {}",
                                variants.size(), event.getProductId(), variantStatus);
                    } else {
                        log.warn("No variants found for VARIABLE productId: {}", event.getProductId());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error processing StockStatusUpdatedEvent for productId: {}, variantId: {}",
                    event.getProductId(), event.getVariantId(), e);
            throw e;
        }
    }
}

