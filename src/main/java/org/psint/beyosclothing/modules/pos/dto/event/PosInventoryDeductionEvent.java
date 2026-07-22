package org.psint.beyosclothing.modules.pos.dto.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * POS Inventory Deduction Event
 * <p>
 * Published when a POS order is placed and inventory needs to be deducted.
 * This is a fire-and-forget message sent to the inventory module.
 * <p>
 * Message Flow: POS Module → RabbitMQ → Inventory Module
 *
 * @author Beyos Development Team
 * @version 1.0
 * @since 2026-02-03
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PosInventoryDeductionEvent implements Serializable {

    /**
     * Event type identifier
     */
    private String eventType;

    /**
     * POS order ID (database primary key)
     */
    private Long orderId;

    /**
     * POS order UUID (public identifier)
     */
    private String orderUuid;

    /**
     * List of items to deduct from inventory
     */
    private List<InventoryDeductionItem> items;

    /**
     * Timestamp when the event was created (ISO-8601 format)
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private LocalDateTime timestamp;

    /**
     * Individual inventory deduction item
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InventoryDeductionItem implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * Product ID
         */
        private Long productId;

        /**
         * Variant ID (null for simple products)
         */
        private Long variantId;

        /**
         * Quantity to deduct
         */
        private Integer quantity;
    }
}
