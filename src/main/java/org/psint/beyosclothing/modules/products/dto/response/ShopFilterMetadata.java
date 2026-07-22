package org.psint.beyosclothing.modules.products.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Shop Filter Metadata Response DTO
 * Contains all available filter options for the shop page sidebar
 *
 * @author Beyos Development Team
 * @version 1.0
 * @since 2026-02-18
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopFilterMetadata {

    private List<CategoryInfo> categories;
    private List<AttributeInfo> attributes;
    private PriceRangeInfo priceRange;

    /**
     * Category information with subcategories
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryInfo {
        private String uuid;
        private String name;
        private String slug;
        private List<SubCategoryInfo> subCategories;
    }

    /**
     * Subcategory information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubCategoryInfo {
        private String uuid;
        private String name;
        private String slug;
    }

    /**
     * Attribute information with values
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttributeInfo {
        private String uuid;
        private String name;
        private List<AttributeValueInfo> values;
    }

    /**
     * Attribute value information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttributeValueInfo {
        private String uuid;
        private String value;
    }

    /**
     * Price range information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PriceRangeInfo {
        private BigDecimal minPrice;
        private BigDecimal maxPrice;
    }
}

