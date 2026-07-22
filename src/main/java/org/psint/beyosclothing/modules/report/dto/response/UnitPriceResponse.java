package org.psint.beyosclothing.modules.report.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Unit-price column for the sales-details table.
 * SIMPLE products carry a single {@code value}; VARIABLE products carry a
 * sold-price {@code min}/{@code max} range. Unused fields are omitted from JSON.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UnitPriceResponse {
    private BigDecimal value;
    private BigDecimal min;
    private BigDecimal max;
    private String display;
}
