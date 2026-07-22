package org.psint.beyosclothing.modules.orders.dto.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryUpdateRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private String requestId;
    private List<InventoryItem> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InventoryItem implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long productId;
        private Long variantId;
        private Integer quantity;
    }
}

