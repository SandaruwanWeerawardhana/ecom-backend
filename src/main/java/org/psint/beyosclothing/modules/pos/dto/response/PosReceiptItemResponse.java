package org.psint.beyosclothing.modules.pos.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Single line item within a printed POS receipt
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PosReceiptItemResponse {
    private Integer lineNo;
    private String product;
    private String sku;
    private BigDecimal price;
    private Integer qty;
    private BigDecimal subtotal;
}
