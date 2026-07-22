package org.psint.beyosclothing.modules.inventory.service;

import org.psint.beyosclothing.modules.inventory.dto.response.ProductDamageStockPageResponse;
import org.psint.beyosclothing.modules.inventory.dto.response.ProductLowStockPageResponse;
import org.psint.beyosclothing.modules.inventory.dto.response.ProductStockPageResponse;
import org.psint.beyosclothing.modules.products.events.ProductCreatedEvent;
import org.psint.beyosclothing.modules.products.events.ProductUpdatedEvent;
import org.psint.beyosclothing.modules.products.events.VariantCreatedEvent;
import org.springframework.data.domain.Pageable;

/**
 * Inventory Service
 * Manages product stock, stock movements, and inventory-related operations
 */
public interface InventoryService {

    /**
     * Initialize stock for a new product
     */
    void initializeProductStock(ProductCreatedEvent event);

    /**
     * Initialize stock for a new variant
     */
    void initializeVariantStock(VariantCreatedEvent event);

    /**
     * Update stock when product is updated
     */
    void handleProductUpdate(ProductUpdatedEvent event);

    /**
     * Handle product deletion (soft delete stock records)
     */
    void handleProductDeletion(Long productId);

    /**
     * Update stock quantity
     */
    void updateStockQuantity(Long productId, Long variantId, Integer quantity, String movementType, String performedBy, String notes);

    void updateStockQuantity(String productStockUuid, Integer quantity, String movementType, String performedBy, String notes);


    void updateStockStatus(String productStockUuid,String status);

    /**
     * Check and create low stock alert
     */
    void checkAndCreateLowStockAlert(Long productId, Long variantId);

    /**
     * Get stock quantity for product variant
     */
    Integer getStockQuantity(Long productId, Long variantId);

    /**
     * Reserve stock for order
     */
    boolean reserveStock(Long productId, Long variantId, Integer quantity, Long orderId);

    /**
     * Release reserved stock
     */
    void releaseStock(Long productId, Long variantId, Integer quantity, String reason);

    /**
     * List product stock with pagination. When `pageable` is unpaged or caller sets all=true,
     * the method returns all rows wrapped in a paged response.
     */
    ProductStockPageResponse listProductStocks(Pageable pageable);

    ProductLowStockPageResponse getLowProductStocks(Pageable pageable);

    ProductDamageStockPageResponse getDamageProductStocks(Pageable pageable);
}
