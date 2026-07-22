package org.psint.beyosclothing.modules.inventory.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * Request DTO for inventory update (from Order module)
 */
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
        @Serial
        private static final long serialVersionUID = 1L;

        private Long productId;
        private Long variantId;
        private Integer quantity;
    }
}

