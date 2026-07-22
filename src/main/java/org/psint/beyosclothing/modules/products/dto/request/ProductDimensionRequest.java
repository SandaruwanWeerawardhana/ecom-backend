package org.psint.beyosclothing.modules.products.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDimensionRequest {
    private BigDecimal weightKg;
    private BigDecimal lengthCm;
    private BigDecimal widthCm;
    private BigDecimal heightCm;
}

