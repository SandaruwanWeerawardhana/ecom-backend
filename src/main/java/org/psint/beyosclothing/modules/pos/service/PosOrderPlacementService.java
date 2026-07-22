package org.psint.beyosclothing.modules.pos.service;

import org.psint.beyosclothing.modules.pos.dto.request.PosPlaceOrderRequest;
import org.psint.beyosclothing.modules.pos.dto.response.PosOrderResponse;

/**
 * POS Order Placement Service
 * Handles order creation with comprehensive validation
 * Ensures correctness, consistency, and prevents race conditions
 */
public interface PosOrderPlacementService {

    /**
     * Place a POS order with comprehensive validation
     * Executes within a single transaction to guarantee all-or-nothing behavior
     *
     * Validation steps:
     * 1. Cart exists and is active
     * 2. Cart contains at least one item
     * 3. Payment method exists and is active
     * 4. Card details valid (if payment method is CARD)
     * 5. Customer exists (if customerId provided)
     * 6. Final real-time stock validation for all items
     *
     * @param request Order placement request
     * @return Order response with receipt details
     * @throws org.psint.beyosclothing.modules.pos.exception.CartNotFoundException if cart not found
     * @throws org.psint.beyosclothing.modules.pos.exception.EmptyCartException if cart has no items
     * @throws org.psint.beyosclothing.modules.pos.exception.PaymentMethodNotFoundException if payment method invalid
     * @throws org.psint.beyosclothing.modules.pos.exception.InvalidCardDetailsException if card details invalid
     * @throws org.psint.beyosclothing.modules.pos.exception.CustomerNotFoundException if customer not found
     * @throws org.psint.beyosclothing.modules.pos.exception.InsufficientStockException if stock unavailable
     */
    PosOrderResponse placeOrder(PosPlaceOrderRequest request);
}
