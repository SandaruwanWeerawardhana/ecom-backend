package org.psint.beyosclothing.modules.products.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for creating a product attribute with its values in one operation
 * Example: Create "Size" attribute with values ["S", "M", "L", "XL"]
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAttributeWithValuesRequest {

    @NotBlank(message = "Attribute name is required")
    @Size(min = 2, max = 255, message = "Attribute name must be between 2 and 255 characters")
    private String attributeName; // Name of the attribute - e.g., "Size", "Color"

    @NotEmpty(message = "At least one attribute value is required")
    private List<@NotBlank(message = "Attribute value cannot be blank")
                 @Size(min = 1, max = 255, message = "Attribute value must be between 1 and 255 characters")
                 String> values; // List of values - e.g., ["Small", "Medium", "Large"]
}

