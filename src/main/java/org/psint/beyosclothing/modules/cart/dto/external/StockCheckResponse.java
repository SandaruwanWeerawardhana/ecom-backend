package org.psint.beyosclothing.modules.cart.dto.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Response from Inventory module with stock information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockCheckResponse implements Serializable {
    private String requestId;
    private Long productId;
    private Long variantId;
    private Integer availableStock;
    private Boolean isAvailable;
    private Boolean allowBackorder;
    private Boolean found;
}

