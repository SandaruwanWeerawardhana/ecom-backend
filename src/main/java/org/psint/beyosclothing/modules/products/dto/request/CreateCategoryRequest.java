package org.psint.beyosclothing.modules.products.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a product category
 * Used when creating both main categories and sub-categories
 * Slug is optional - will be auto-generated from name if not provided
 * */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCategoryRequest {

    @NotBlank(message = "Category name is required")
    @Size(min = 2, max = 255, message = "Category name must be between 2 and 255 characters")
    private String name; // Category name - e.g., "Men's Clothing" or "T-Shirts"

    @Size(min = 2, max = 255, message = "Slug must be between 2 and 255 characters")
    private String slug; // URL-friendly slug - e.g., "mens-clothing" or "t-shirts"

    private String parentCategoryUuid; // UUID of parent category if this is a sub-category (null for main categories)

    private String imageUrl; // Optional image URL or S3 object key for category image
}
