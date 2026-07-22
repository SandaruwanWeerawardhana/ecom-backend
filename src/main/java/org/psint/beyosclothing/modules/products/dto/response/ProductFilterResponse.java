package org.psint.beyosclothing.modules.products.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Product Filter Response DTO
 * Optimized response for product filtering and listing on e-commerce frontend
 *
 * @author Beyos Development Team
 * @version 1.0
 * @since 2026-02-03
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductFilterResponse {

    private String uuid;
    private String name;
    private String slug;
    private String category;
    private String subCategory;
    private BigDecimal salePrice;
    private BigDecimal showcasePrice;
    private Integer discount;
    private Double rating;
    private Long reviews;
    private List<String> colors;
    private List<String> sizes;
    private Boolean inStock;
    private Boolean featured;
    private String image;
    private String description;
}
