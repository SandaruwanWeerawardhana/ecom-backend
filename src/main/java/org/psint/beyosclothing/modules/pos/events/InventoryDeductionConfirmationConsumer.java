package org.psint.beyosclothing.modules.pos.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.pos.dto.event.InventoryDeductionConfirmationEvent;
import org.psint.beyosclothing.modules.pos.service.PosInventoryEventService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Inventory Deduction Confirmation Consumer
 * Listens for confirmation events from inventory module
 * Updates pos_stock_sync records based on deduction results
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryDeductionConfirmationConsumer {

    private final PosInventoryEventService inventoryEventService;

    @RabbitListener(queues = "pos.inventory.confirmation.queue")
    public void handleInventoryDeductionConfirmation(InventoryDeductionConfirmationEvent event) {
        log.info("Received inventory deduction confirmation - order: {}, success: {}",
                event.getOrderUuid(), event.isSuccess());

        try {
            inventoryEventService.handleDeductionConfirmation(
                    event.getOrderId(),
                    event.getOrderUuid(),
                    event.isSuccess(),
                    event.getErrorMessage()
            );

            log.info("Processed inventory deduction confirmation for order: {}", event.getOrderUuid());

        } catch (Exception e) {
            log.error("Failed to process inventory deduction confirmation for order {}: {}",
                    event.getOrderUuid(), e.getMessage(), e);
        }
    }
}
