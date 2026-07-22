package org.psint.beyosclothing.modules.products.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating a product category
 * Slug is optional - will be auto-generated from name if not provided
 * */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCategoryRequest {

    @NotBlank(message = "Category name is required")
    @Size(min = 2, max = 255, message = "Category name must be between 2 and 255 characters")
    private String name; // Updated category name
    // Optional: Updated URL-friendly slug. If not provided, auto-generated from name

    @Size(min = 2, max = 255, message = "Slug must be between 2 and 255 characters")
    private String slug; // Updated URL-friendly slug

    private String parentCategoryUuid; // UUID of parent category (can be changed)

    private String imageUrl; // Updated image URL for the category
}

