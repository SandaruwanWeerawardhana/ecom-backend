package org.psint.beyosclothing.modules.pos.service;

public interface PosStockValidationService {
    /**
     * Validate stock for product/variant with requested quantity.
     * Returns true when sufficient stock available, false otherwise.
     */
    boolean validateStock(Long productId, Long variantId, int requestedQuantity);
}
