package org.psint.beyosclothing.modules.pos.dto.document;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PosProductDocument {
    private String id;
    private Long productId;
    private String uuid;
    private String title;
    private String sku;
    private String description;
    private BigDecimal price;
    private BigDecimal salePrice;
    private Long stockAvailable;
    private String thumbnailUrl;
    private String category;
    private List<String> tags;
    private Boolean isActive;
}
