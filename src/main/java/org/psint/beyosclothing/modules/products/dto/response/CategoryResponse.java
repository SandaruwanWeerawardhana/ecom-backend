package org.psint.beyosclothing.modules.products.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for product category
 * Contains category information with hierarchy
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResponse {

    private String uuid; // Unique identifier for the category

    private String name; // Category name

    private String slug; // URL-friendly slug

    private String parentCategoryUuid; // UUID of parent category (null if main category)

    private String parentCategoryName; // Name of parent category (null if main category)

    private Boolean isActive; // Whether the category is active

    private String imageUrl; // Image URL (S3 public URL or presigned URL)

    private LocalDateTime dateCreated; // When the category was created

    private LocalDateTime dateUpdated; // When the category was last updated

    private List<String> subCategories; // List of sub-category UUIDs (if any)

    private Long productCount;
}
