package org.psint.beyosclothing.modules.products.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for adding new values to an existing attribute
 * Used when you want to add more values to an existing attribute
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddAttributeValuesRequest {

    @NotEmpty(message = "At least one attribute value is required")
    private List<@Size(min = 1, max = 255, message = "Attribute value must be between 1 and 255 characters")
                 String> values; // List of new values to add
}

