package org.psint.beyosclothing.modules.inventory.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.inventory.dto.response.*;
// ...existing code... (removed unused import)
import org.psint.beyosclothing.modules.inventory.entity.*;
import org.psint.beyosclothing.modules.inventory.events.StockStatusUpdatedEvent;
import org.psint.beyosclothing.modules.inventory.repository.*;
import org.psint.beyosclothing.modules.inventory.service.InventoryService;
import org.psint.beyosclothing.modules.inventory.dto.external.InventoryProductLookupRequest;
import org.psint.beyosclothing.modules.inventory.dto.external.InventoryProductLookupResponse;
import org.psint.beyosclothing.modules.inventory.service.InventoryProductLookupService;
import org.psint.beyosclothing.modules.products.events.ProductCreatedEvent;
import org.psint.beyosclothing.modules.products.events.ProductUpdatedEvent;
import org.psint.beyosclothing.modules.products.events.VariantCreatedEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Inventory Service Implementation
 * Handles all inventory-related operations for products
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class InventoryServiceImpl implements InventoryService {

    private final ProductStockRepository stockRepository;
    private final StockMovementLogRepository movementLogRepository;
    private final LowStockRepository lowStockRepository;
    private final BackorderRepository backorderRepository;
    private final InventoryProductLookupService inventoryProductLookupService; // cross-module via RabbitMQ
    private final DamagedInventoryRepository damagedInventoryRepository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchange.inventory}")
    private String inventoryExchange;

    @Value("${app.business.inventory.low-stock-threshold:10}")
    private Integer defaultLowStockThreshold;

    @Override
    public void initializeProductStock(ProductCreatedEvent event) {
        log.info("Initializing stock for product: {} (UUID: {})", event.getProductId(), event.getUuid());

        try {
            // For SIMPLE products, create one stock record without variant
            if ("SIMPLE".equals(event.getProductType())) {
                Integer initialQuantity = event.getInitialStockQuantity() != null ? event.getInitialStockQuantity() : 0;
                createStockRecord(event.getProductId(), null, initialQuantity);
                log.info("Stock initialized for SIMPLE product: {} with quantity: {}", event.getProductId(), initialQuantity);
            } else {
                // For VARIABLE products, stock will be initialized when variants are created
                // This is handled separately when variants are added
                log.info("VARIABLE product detected: {}. Stock will be initialized per variant.", event.getProductId());
            }
        } catch (Exception e) {
            log.error("Error initializing stock for product: {}", event.getProductId(), e);
        }
    }

    @Override
    public void initializeVariantStock(VariantCreatedEvent event) {
        log.info("Initializing stock for variant: {} (Product: {})", event.getVariantId(), event.getProductId());

        try {
            // Create stock record for the variant with the provided initial quantity
            ProductStockEntity stock = ProductStockEntity.builder()
                    .productId(event.getProductId())
                    .variantId(event.getVariantId())
                    .stockQuantity(event.getInitialStockQuantity() != null ? event.getInitialStockQuantity() : 0)
                    .allowBackorder(event.getAllowBackorder() != null ? event.getAllowBackorder() : false)
                    .lowStockThreshold(event.getLowStockThreshold() != null ? event.getLowStockThreshold() : defaultLowStockThreshold)
                    .isActive(true)
                    .build();

            stock = stockRepository.save(stock);
            log.info("Stock initialized for variant: {}, initial quantity: {}", event.getVariantId(), event.getInitialStockQuantity());

            // Create initial movement log if quantity > 0
            if (event.getInitialStockQuantity() != null && event.getInitialStockQuantity() > 0) {
                createMovementLog(
                    event.getProductId(),
                    event.getVariantId(),
                    "IN",
                    event.getInitialStockQuantity(),
                    0,
                    event.getInitialStockQuantity(),
                    event.getCreatedBy(),
                    "Initial stock for variant"
                );
            }

            // Check for low stock alert
            if (stock.getStockQuantity() <= stock.getLowStockThreshold()) {
                checkAndCreateLowStockAlert(event.getProductId(), event.getVariantId());
            }
        } catch (Exception e) {
            log.error("Error initializing stock for variant: {}", event.getVariantId(), e);
        }
    }

    @Override
    public void handleProductUpdate(ProductUpdatedEvent event) {
        log.info("Handling product update for product: {}", event.getProductId());

        // Check if stock records exist, if not create them
        Optional<ProductStockEntity> existingStock = stockRepository.findByProductIdAndVariantIdIsNull(event.getProductId());

        if (existingStock.isEmpty()) {
            log.warn("No stock record found for product: {}. Creating new stock record.", event.getProductId());
            createStockRecord(event.getProductId(), null, 0);
        }
    }

    @Override
    public void handleProductDeletion(Long productId) {
        log.info("Handling product deletion for product: {}", productId);

        try {
            // Soft delete all stock records for this product
            stockRepository.findByProductId(productId).forEach(stock -> {
                stock.setIsActive(false);
                stockRepository.save(stock);
                log.info("Deactivated stock record for product: {}, variant: {}", productId, stock.getVariantId());
            });
        } catch (Exception e) {
            log.error("Error handling product deletion for product: {}", productId, e);
        }
    }

    @Override
    public void updateStockQuantity(Long productId, Long variantId, Integer quantity,
                                     String movementType, String performedBy, String notes) {
        log.info("Updating stock for product: {}, variant: {}, quantity: {}, type: {}",
                 productId, variantId, quantity, movementType);

        try {
            // Find or create stock record
            ProductStockEntity stock = findOrCreateStockRecord(productId, variantId);

            Integer oldQuantity = stock.getStockQuantity();
            Integer newQuantity = calculateNewQuantity(oldQuantity, quantity, movementType);

            // Update stock quantity
            stock.setStockQuantity(newQuantity);
            stock = stockRepository.save(stock);

            // Create movement log
            createMovementLog(productId, variantId, movementType, quantity, oldQuantity, newQuantity, performedBy, notes);

            // Check for low stock alert
            if (stock.getLowStockThreshold() != null && newQuantity <= stock.getLowStockThreshold()) {
                checkAndCreateLowStockAlert(productId, variantId);
            }

            log.info("Stock updated successfully for product: {}, variant: {}. Old: {}, New: {}",
                     productId, variantId, oldQuantity, newQuantity);
        } catch (Exception e) {
            log.error("Error updating stock for product: {}, variant: {}", productId, variantId, e);
            throw new RuntimeException("Failed to update stock", e);
        }
    }

    @Override
    public void updateStockQuantity(String productStockUuid, Integer quantity, String movementType, String performedBy, String notes) {
        log.info("Updating stock by productStockUuid: {}, quantity: {}, type: {}", productStockUuid, quantity, movementType);

        ProductStockEntity stock = stockRepository.findByUuid(productStockUuid)
                .orElseThrow(() -> new RuntimeException("Inventory record not found for UUID: " + productStockUuid));

        Long productId = stock.getProductId();
        Long variantId = stock.getVariantId();

        log.info("Resolved productStockUuid: {} → productId: {}, variantId: {}", productStockUuid, productId, variantId);

        updateStockQuantity(productId, variantId, quantity, movementType, performedBy, notes);
    }

    @Override
    public void updateStockStatus(String productStockUuid, String status) {
        log.info("Updating stock status for UUID: {}, status: {}", productStockUuid, status);

        ProductStockEntity stock = stockRepository.findByUuid(productStockUuid)
                .orElseThrow(() -> new RuntimeException("Inventory record not found for UUID: " + productStockUuid));

        // IN_STOCK is the only "active" state; everything else deactivates the stock record
        boolean isActive = "IN_STOCK".equalsIgnoreCase(status);
        stock.setIsActive(isActive);
        stock = stockRepository.save(stock);

        log.info("Stock status updated for UUID: {} → status: {}", productStockUuid, status);

        try {
            // Look up product type via cross-module RPC to drive consumer-side update logic
            String productType = null;
            InventoryProductLookupResponse productInfo =
                    inventoryProductLookupService.lookupProductInfo(stock.getProductId(), stock.getVariantId());
            if (productInfo != null && productInfo.getProductType() != null) {
                productType = productInfo.getProductType();
            }

            StockStatusUpdatedEvent event = StockStatusUpdatedEvent.builder()
                    .productId(stock.getProductId())
                    .variantId(stock.getVariantId())
                    .productType(productType)
                    .status(status.toUpperCase())
                    .updatedAt(java.time.LocalDateTime.now())
                    .build();

            rabbitTemplate.convertAndSend(inventoryExchange, "inventory.stock.status.update", event);

            log.info("Published StockStatusUpdatedEvent → productId: {}, variantId: {}, productType: {}, status: {}",
                    stock.getProductId(), stock.getVariantId(), productType, status);
        } catch (Exception e) {
            log.error("Failed to publish StockStatusUpdatedEvent for UUID: {}", productStockUuid, e);
        }
    }

    @Override
    public void checkAndCreateLowStockAlert(Long productId, Long variantId) {
        log.info("Checking low stock alert for product: {}, variant: {}", productId, variantId);

        try {
            ProductStockEntity stock = stockRepository.findByProductIdAndVariantId(productId, variantId)
                    .orElse(null);

            if (stock == null || stock.getLowStockThreshold() == null) {
                return;
            }

            if (stock.getStockQuantity() <= stock.getLowStockThreshold()) {
                // Check if alert already exists
                boolean alertExists = lowStockRepository
                        .existsByProductIdAndVariantIdAndAlertStatus(
                                productId, variantId, LowStockEntity.AlertStatus.PENDING
                        );

                if (!alertExists) {
                    LowStockEntity alert = LowStockEntity.builder()
                            .productId(productId)
                            .variantId(variantId)
                            .currentStock(stock.getStockQuantity())
                            .thresholdLevel(stock.getLowStockThreshold())
                            .alertStatus(LowStockEntity.AlertStatus.PENDING)
                            .notified(false)
                            .isActive(true)
                            .build();

                    lowStockRepository.save(alert);
                    log.info("Low stock alert created for product: {}, variant: {}", productId, variantId);
                }
            }
        } catch (Exception e) {
            log.error("Error checking low stock alert for product: {}, variant: {}", productId, variantId, e);
        }
    }

    @Override
    public Integer getStockQuantity(Long productId, Long variantId) {
        return stockRepository.findByProductIdAndVariantId(productId, variantId)
                .map(ProductStockEntity::getStockQuantity)
                .orElse(0);
    }

    @Override
    public boolean reserveStock(Long productId, Long variantId, Integer quantity, Long orderId) {
        log.info("Reserving stock for product: {}, variant: {}, quantity: {}, order: {}",
                 productId, variantId, quantity, orderId);

        try {
            ProductStockEntity stock = stockRepository.findByProductIdAndVariantId(productId, variantId)
                    .orElseThrow(() -> new RuntimeException("Stock not found"));

            if (stock.getStockQuantity() >= quantity) {
                // Reduce stock
                updateStockQuantity(productId, variantId, quantity, "OUT", "SYSTEM", "Reserved for order: " + orderId);
                return true;
            } else if (stock.getAllowBackorder()) {
                // Create backorder
                BackOrderEntity backorder = BackOrderEntity.builder()
                        .productId(productId)
                        .variantId(variantId)
                        .orderId(orderId)
                        .quantity(quantity - stock.getStockQuantity())
                        .fulfilled(false)
                        .isActive(true)
                        .build();
                backorderRepository.save(backorder);

                // Reserve available stock
                if (stock.getStockQuantity() > 0) {
                    updateStockQuantity(productId, variantId, stock.getStockQuantity(), "OUT", "SYSTEM",
                                      "Partial reserve for order: " + orderId);
                }

                log.info("Backorder created for product: {}, variant: {}, quantity: {}",
                         productId, variantId, quantity - stock.getStockQuantity());
                return true;
            }

            log.warn("Insufficient stock for product: {}, variant: {}. Required: {}, Available: {}",
                     productId, variantId, quantity, stock.getStockQuantity());
            return false;
        } catch (Exception e) {
            log.error("Error reserving stock for product: {}, variant: {}", productId, variantId, e);
            return false;
        }
    }

    @Override
    public void releaseStock(Long productId, Long variantId, Integer quantity, String reason) {
        log.info("Releasing stock for product: {}, variant: {}, quantity: {}, reason: {}",
                 productId, variantId, quantity, reason);
        updateStockQuantity(productId, variantId, quantity, "IN", "SYSTEM", reason);
    }

    @Override
    public ProductStockPageResponse listProductStocks(Pageable pageable) {
        log.info("Listing product stocks, page: {}, size: {}", pageable == null ? -1 : pageable.getPageNumber(), pageable == null ? -1 : pageable.getPageSize());

        try {
            List<ProductStockEntity> allStocks = pageable != null && pageable.getSort().isSorted()
                    ? stockRepository.findAll(pageable.getSort())
                    : stockRepository.findAll();

            Map<String, InventoryProductLookupResponse> productInfoByKey = inventoryProductLookupService.lookupProductInfoBulk(
                    allStocks.stream()
                            .map(stock -> InventoryProductLookupRequest.LookupItem.builder()
                                    .productId(stock.getProductId())
                                    .variantId(stock.getVariantId())
                                    .build())
                            .collect(Collectors.toList()));

            List<ProductStockEntity> filteredStocks = allStocks.stream()
                    .filter(stock -> shouldIncludeStockRecord(stock, productInfoByKey.get(
                            productLookupKey(stock.getProductId(), stock.getVariantId()))))
                    .collect(Collectors.toList());

            List<ProductStockEntity> pageStocks = paginateStocks(filteredStocks, pageable);

            List<ProductStockListResponse> items = new ArrayList<>();
            for (ProductStockEntity stock : pageStocks) {
                InventoryProductLookupResponse productInfo = productInfoByKey.get(
                        productLookupKey(stock.getProductId(), stock.getVariantId()));

                ProductStockListResponse dto = new ProductStockListResponse();
                dto.setId(stock.getUuid());
                dto.setProductName(resolveProductName(productInfo));
                dto.setSku(productInfo != null ? productInfo.getSku() : null);
                dto.setAttributeSummary(productInfo != null ? productInfo.getAttributeSummary() : null);
                dto.setStockQuantity(stock.getStockQuantity());
                dto.setInventoryStatus(resolveInventoryStatus(stock, productInfo));
                dto.setIsActive(stock.getIsActive());
                dto.setDateCreated(stock.getDateCreated());
                dto.setDateUpdated(stock.getDateUpdated());

                items.add(dto);
            }

            return ProductStockPageResponse.builder()
                    .items(items)
                    .page(pageable != null && pageable.isPaged() ? pageable.getPageNumber() : 0)
                    .size(pageable != null && pageable.isPaged() ? pageable.getPageSize() : items.size())
                    .totalElements(filteredStocks.size())
                    .totalPages(resolveTotalPages(filteredStocks.size(), pageable))
                    .build();
        } catch (Exception e) {
            log.error("Error listing product stocks", e);
            throw new RuntimeException("Failed to list product stocks", e);
        }
    }

    @Override
    public ProductLowStockPageResponse getLowProductStocks(Pageable pageable) {
        Pageable resolved = (pageable == null || pageable.isUnpaged())
                ? Pageable.unpaged() : pageable;

        log.info("Getting low product stocks — page: {}, size: {}",
                resolved.isPaged() ? resolved.getPageNumber() : -1,
                resolved.isPaged() ? resolved.getPageSize() : -1);

        try {
            List<LowStockEntity> allAlerts = lowStockRepository.findAll();

            List<LowStockEntity> pageItems;
            int page;
            int size;
            int totalPages = 1;

            if (resolved.isPaged()) {
                page = Math.max(0, resolved.getPageNumber());
                size = Math.max(1, resolved.getPageSize());
                int from = page * size;
                if (from >= allAlerts.size()) {
                    pageItems = new ArrayList<>();
                } else {
                    int to = Math.min(from + size, allAlerts.size());
                    pageItems = allAlerts.subList(from, to);
                }
                totalPages = (int) Math.ceil((double) allAlerts.size() / size);
            } else {
                pageItems = allAlerts;
            }

            List<ProductLowStockResponse> items = new ArrayList<>();
            for (LowStockEntity alert : pageItems) {
                InventoryProductLookupResponse productInfo = inventoryProductLookupService.lookupProductInfo(
                        alert.getProductId(), alert.getVariantId());

                ProductLowStockResponse dto = new org.psint.beyosclothing.modules.inventory.dto.response.ProductLowStockResponse();
                dto.setUuId(alert.getUuid());
                dto.setProductName(productInfo != null ? productInfo.getProductTitle() : null);
                dto.setSku(productInfo != null ? productInfo.getSku() : null);
                dto.setAttributeSummary(productInfo != null ? productInfo.getAttributeSummary() : null);
                dto.setStockQuantity(alert.getCurrentStock());
                dto.setThresholdLevel(alert.getThresholdLevel() != null ? String.valueOf(alert.getThresholdLevel()) : null);
                dto.setAlertStatus(alert.getAlertStatus() != null ? alert.getAlertStatus().name() : null);
                dto.setIsActive(alert.getIsActive());
                dto.setDateCreated(alert.getDateCreated());
                dto.setDateUpdated(alert.getDateUpdated());
                items.add(dto);
            }

            return ProductLowStockPageResponse.builder()
                    .items(items)
                    .page(resolved.isPaged() ? resolved.getPageNumber() : 0)
                    .size(resolved.isPaged() ? resolved.getPageSize() : items.size())
                    .totalElements(allAlerts.size())
                    .totalPages(resolved.isPaged() ? totalPages : 1)
                    .build();
        } catch (Exception e) {
            log.error("Error getting low product stocks", e);
            throw new RuntimeException("Failed to get low product stocks", e);
        }
    }

    @Override
    public ProductDamageStockPageResponse getDamageProductStocks(Pageable pageable) {
        Pageable resolved = (pageable == null || pageable.isUnpaged()) ? Pageable.unpaged() : pageable;

        log.info("Getting damaged product stocks — page: {}, size: {}",
                resolved.isPaged() ? resolved.getPageNumber() : -1,
                resolved.isPaged() ? resolved.getPageSize() : -1);

        try {
            List<DamagedInventoryEntity> allDamaged = damagedInventoryRepository.findAllActiveDamagedItems();

            List<DamagedInventoryEntity> pageItems;
            int page = 0;
            int size;
            int totalPages = 1;

            if (resolved.isPaged()) {
                page = Math.max(0, resolved.getPageNumber());
                size = Math.max(1, resolved.getPageSize());
                int from = page * size;
                if (from >= allDamaged.size()) {
                    pageItems = new ArrayList<>();
                } else {
                    int to = Math.min(from + size, allDamaged.size());
                    pageItems = allDamaged.subList(from, to);
                }
                totalPages = (int) Math.ceil((double) allDamaged.size() / size);
            } else {
                pageItems = allDamaged;
            }

            List<ProductDamageStockResponse> items = new ArrayList<>();
            for (DamagedInventoryEntity damaged : pageItems) {
                InventoryProductLookupResponse productInfo = inventoryProductLookupService.lookupProductInfo(
                        damaged.getProductId(), damaged.getVariantId());

                ProductDamageStockResponse dto = new ProductDamageStockResponse();
                dto.setUuId(damaged.getUuid());
                dto.setProductName(productInfo != null ? productInfo.getProductTitle() : null);
                dto.setSku(productInfo != null ? productInfo.getSku() : null);
                dto.setAttributeSummary(productInfo != null ? productInfo.getAttributeSummary() : null);
                dto.setStockQuantity(damaged.getQuantity());
                dto.setReason(damaged.getReason());
                dto.setIsActive(damaged.getIsActive());
                dto.setDateCreated(damaged.getDateCreated());
                dto.setDateUpdated(damaged.getDateUpdated());
                items.add(dto);
            }

            return ProductDamageStockPageResponse.builder()
                    .items(items)
                    .page(resolved.isPaged() ? resolved.getPageNumber() : 0)
                    .size(resolved.isPaged() ? resolved.getPageSize() : items.size())
                    .totalElements(allDamaged.size())
                    .totalPages(resolved.isPaged() ? totalPages : 1)
                    .build();
        } catch (Exception e) {
            log.error("Error getting damaged product stocks", e);
            throw new RuntimeException("Failed to get damaged product stocks", e);
        }
    }

    // Helper Methods

    private ProductStockEntity findOrCreateStockRecord(Long productId, Long variantId) {
        Optional<ProductStockEntity> existing;

        if (variantId == null) {
            existing = stockRepository.findByProductIdAndVariantIdIsNull(productId);
        } else {
            existing = stockRepository.findByProductIdAndVariantId(productId, variantId);
        }

        return existing.orElseGet(() -> createStockRecord(productId, variantId, 0));
    }

    private ProductStockEntity createStockRecord(Long productId, Long variantId, Integer initialQuantity) {
        ProductStockEntity stock = ProductStockEntity.builder()
                .productId(productId)
                .variantId(variantId)
                .stockQuantity(initialQuantity)
                .allowBackorder(false)
                .lowStockThreshold(defaultLowStockThreshold)
                .isActive(true)
                .build();

        stock = stockRepository.save(stock);
        log.info("Created stock record for product: {}, variant: {}, initial quantity: {}",
                 productId, variantId, initialQuantity);

        // Create initial movement log
        if (initialQuantity > 0) {
            createMovementLog(productId, variantId, "IN", initialQuantity, 0, initialQuantity,
                            "SYSTEM", "Initial stock");
        }

        return stock;
    }

    private void createMovementLog(Long productId, Long variantId, String movementType,
                                   Integer quantityChanged, Integer quantityBefore, Integer quantityAfter,
                                   String performedBy, String notes) {
        StockMovementLogEntity.MovementType type;
        try {
            type = StockMovementLogEntity.MovementType.valueOf(movementType);
        } catch (IllegalArgumentException e) {
            type = StockMovementLogEntity.MovementType.ADJUSTMENT;
        }

        StockMovementLogEntity log = StockMovementLogEntity.builder()
                .productId(productId)
                .variantId(variantId)
                .movementType(type)
                .quantityChanged(Math.abs(quantityChanged))
                .quantityBefore(quantityBefore)
                .quantityAfter(quantityAfter)
                .performedBy(performedBy)
                .notes(notes)
                .isActive(true)
                .build();

        movementLogRepository.save(log);
    }

    private Integer calculateNewQuantity(Integer currentQuantity, Integer changeAmount, String movementType) {
        return switch (movementType.toUpperCase()) {
            case "IN", "RETURN" -> currentQuantity + changeAmount;
            case "OUT" -> Math.max(0, currentQuantity - changeAmount);
            case "ADJUSTMENT" -> changeAmount; // Direct set
            default -> currentQuantity;
        };
    }

    private boolean shouldIncludeStockRecord(ProductStockEntity stock, InventoryProductLookupResponse productInfo) {
        if (productInfo == null || productInfo.getProductType() == null) {
            return true;
        }

        if ("VARIABLE".equalsIgnoreCase(productInfo.getProductType())) {
            return stock.getVariantId() != null;
        }

        if ("SIMPLE".equalsIgnoreCase(productInfo.getProductType())) {
            return stock.getVariantId() == null;
        }

        return true;
    }

    private List<ProductStockEntity> paginateStocks(List<ProductStockEntity> stocks, Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return stocks;
        }

        int page = Math.max(0, pageable.getPageNumber());
        int size = Math.max(1, pageable.getPageSize());
        int from = page * size;
        if (from >= stocks.size()) {
            return new ArrayList<>();
        }

        int to = Math.min(from + size, stocks.size());
        return stocks.subList(from, to);
    }

    private int resolveTotalPages(int totalElements, Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return 1;
        }

        int size = Math.max(1, pageable.getPageSize());
        return (int) Math.ceil((double) totalElements / size);
    }

    private String resolveInventoryStatus(ProductStockEntity stock, InventoryProductLookupResponse productInfo) {
        if (productInfo != null && productInfo.getInventoryStatus() != null) {
            if ("SIMPLE".equalsIgnoreCase(productInfo.getProductType()) && stock.getVariantId() == null) {
                return productInfo.getInventoryStatus();
            }

            if ("VARIABLE".equalsIgnoreCase(productInfo.getProductType()) && stock.getVariantId() != null) {
                return productInfo.getInventoryStatus();
            }

            return productInfo.getInventoryStatus();
        }

        return "IN_STOCK";
    }

    private String resolveProductName(InventoryProductLookupResponse productInfo) {
        if (productInfo != null && productInfo.getProductTitle() != null) {
            return productInfo.getProductTitle();
        }

        return "Unknown Product";
    }

    private String productLookupKey(Long productId, Long variantId) {
        return productId + ":" + (variantId == null ? "null" : variantId);
    }
}
