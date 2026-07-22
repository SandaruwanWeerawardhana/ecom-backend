package org.psint.beyosclothing.modules.products.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDimensionResponse {
    private String uuid;
    private String weightKg;
    private String lengthCm;
    private String widthCm;
    private String heightCm;
}

