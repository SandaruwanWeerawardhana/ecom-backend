package org.psint.beyosclothing.modules.products.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Shop Filter Request DTO
 * Request object for filtering products on shop page
 *
 * @author Beyos Development Team
 * @version 1.0
 * @since 2026-02-18
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopFilterRequest {

    // Category filter (can be main or sub category UUID)
    private String categoryUuid;

    // Price range filter
    private BigDecimal minPrice;
    private BigDecimal maxPrice;

    // Attribute filters (multiple attributes can be selected)
    // List of attribute value UUIDs
    private List<String> attributeValueUuids;

    // Stock status filter
    private Boolean inStockOnly; // If true, only show in-stock products

    // Pagination
    private Integer page;
    private Integer size;

    // Sorting
    private String sortBy; // e.g., "price", "dateCreated", "popularity"
    private String sortDirection; // "ASC" or "DESC"
}

