package org.psint.beyosclothing.modules.pos.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Shift summary response with sales breakdown
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PosShiftSummaryResponse {
    private String uuid;
    private BigDecimal totalSales;
    private BigDecimal cashSales;
    private BigDecimal cardSales;
    private Integer transactionCount;
}
