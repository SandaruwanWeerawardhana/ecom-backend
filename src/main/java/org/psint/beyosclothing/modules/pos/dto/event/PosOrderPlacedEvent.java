package org.psint.beyosclothing.modules.pos.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * POS Order Placed Event
 * Published to inventory module for asynchronous stock deduction
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PosOrderPlacedEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private String eventType;
    private Long orderId;
    private String orderUuid;
    private List<OrderItemData> items;
    private LocalDateTime timestamp;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemData implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long orderItemId;
        private Long productId;
        private Long variantId;
        private Integer quantity;
    }
}
