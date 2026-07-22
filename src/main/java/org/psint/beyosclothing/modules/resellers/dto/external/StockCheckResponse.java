package org.psint.beyosclothing.modules.resellers.dto.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

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
