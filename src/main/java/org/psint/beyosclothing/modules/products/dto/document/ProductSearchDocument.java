package org.psint.beyosclothing.modules.products.dto.document;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Elasticsearch Document for Product Search
 * Optimized for real-time search with minimal fields
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSearchDocument {

    @JsonProperty("product_id")
    private Long productId;

    @JsonProperty("uuid")
    private String uuid;

    @JsonProperty("sku")
    private String sku;

    @JsonProperty("title")
    private String title;

    @JsonProperty("slug")
    private String slug;

    @JsonProperty("short_description")
    private String shortDescription;

    @JsonProperty("description")
    private String description;

    @JsonProperty("thumbnail_url")
    private String thumbnailUrl;

    @JsonProperty("showcase_price")
    private BigDecimal showcasePrice;

    @JsonProperty("sale_price")
    private BigDecimal salePrice;

    @JsonProperty("category_name")
    private String categoryName;

    @JsonProperty("is_publish")
    private Boolean isPublish;

    @JsonProperty("is_active")
    private Boolean isActive;

    @JsonProperty("featured")
    private Boolean featured;

    @JsonProperty("date_created")
    private Long dateCreated;
}
