package org.psint.beyosclothing.modules.pos.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response DTO for paginated POS product list
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PosProductListResponse {

    private List<PosProductItem> products;
    private Integer currentPage;
    private Integer totalPages;
    private Long totalProducts;
    private Integer pageSize;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PosProductItem {
        private String uuid;
        private String title;
        private String sku;
        private String thumbnailUrl;
        private BigDecimal salePrice;
        private BigDecimal showcasePrice;
        private Integer availableQuantity;
        private String productType; // SIMPLE or VARIABLE
        private Boolean isActive;
        private Boolean hasVariants;
    }
}

