package org.psint.beyosclothing.modules.pos.service;

import org.psint.beyosclothing.modules.orders.entity.OrderEntity;

/**
 * POS Inventory Event Service
 * Handles asynchronous inventory deduction after POS order creation
 */
public interface PosInventoryEventService {

    /**
     * Publish inventory deduction event after order creation
     * This is fire-and-forget - does not block order creation
     * Creates audit records in pos_stock_sync table
     *
     * @param order Created order entity
     */
    void publishInventoryDeductionEvent(OrderEntity order);

    /**
     * Handle inventory deduction confirmation from inventory module
     * Updates pos_stock_sync records based on deduction results
     *
     * @param orderId Order ID
     * @param orderUuid Order UUID
     * @param success Whether deduction succeeded
     * @param errorMessage Error message if failed
     */
    void handleDeductionConfirmation(Long orderId, String orderUuid, boolean success, String errorMessage);

    /**
     * Retry pending inventory deductions
     * Called by scheduled job for unsynced records
     *
     * @return Number of records retried
     */
    int retryPendingDeductions();

    /**
     * Mark order for manual review when inventory sync fails repeatedly
     *
     * @param orderId Order ID
     * @param reason Reason for review
     */
    void markOrderForManualReview(Long orderId, String reason);
}
