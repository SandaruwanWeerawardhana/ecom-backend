package org.psint.beyosclothing.modules.pos.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Printable POS receipt data for an order
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PosReceiptDataResponse {
    private String billNo;
    private LocalDateTime date;
    private List<PosReceiptItemResponse> items;
    private BigDecimal subtotal;
    private BigDecimal grandTotal;
}
