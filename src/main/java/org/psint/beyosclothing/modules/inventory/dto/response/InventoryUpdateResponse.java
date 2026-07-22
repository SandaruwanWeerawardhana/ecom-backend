package org.psint.beyosclothing.modules.inventory.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * Response DTO for inventory update
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryUpdateResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private String requestId;
    private Boolean success;
    private List<FailedItem> failedItems;
    private String errorMessage;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailedItem implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private Long productId;
        private Long variantId;
        private String reason;
    }
}

