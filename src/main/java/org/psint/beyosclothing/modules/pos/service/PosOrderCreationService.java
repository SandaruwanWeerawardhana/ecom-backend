package org.psint.beyosclothing.modules.pos.service;

import org.psint.beyosclothing.modules.orders.entity.OrderEntity;
import org.psint.beyosclothing.modules.pos.dto.request.PosPlaceOrderRequest;
import org.psint.beyosclothing.modules.pos.entity.PosCartEntity;

/**
 * POS Order Creation Service
 * Creates orders from validated POS carts
 * Handles order header, items, and totals with POS-specific rules
 */
public interface PosOrderCreationService {

    /**
     * Create POS order from validated cart
     * Executes within existing transaction from PosOrderPlacementService
     *
     * @param cart Validated cart entity
     * @param request Order placement request
     * @return Created order entity
     */
    OrderEntity createPosOrder(PosCartEntity cart, PosPlaceOrderRequest request);
}
