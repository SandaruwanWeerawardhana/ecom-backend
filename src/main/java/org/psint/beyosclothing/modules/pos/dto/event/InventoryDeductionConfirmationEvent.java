package org.psint.beyosclothing.modules.pos.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Inventory Deduction Confirmation Event
 * Published by inventory module when deduction succeeds or fails
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryDeductionConfirmationEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private String eventType;
    private Long orderId;
    private String orderUuid;
    private boolean success;
    private List<ItemDeductionResult> results;
    private String errorMessage;
    private LocalDateTime timestamp;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemDeductionResult implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long orderItemId;
        private Long productId;
        private Long variantId;
        private Integer quantity;
        private boolean deducted;
        private String errorMessage;
    }
}
