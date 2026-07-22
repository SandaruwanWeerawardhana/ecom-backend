package org.psint.beyosclothing.modules.pos.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * POS Cart Item Response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PosCartItemResponse {
    private String uuid;
    private Long productId;
    private String productName;
    private String productSku;
    private Long variantId;
    private String attributeSummary;
    private Integer quantity;
    private Double unitPrice;
    private Double totalPrice;
    private Integer stockAvailable;
    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime updatedAt;
}
