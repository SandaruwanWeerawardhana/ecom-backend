package org.psint.beyosclothing.modules.products.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for updating an existing product attribute
 * Used to update attribute name, activation status, and attribute values
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAttributeRequest {

    @Size(min = 2, max = 255, message = "Attribute name must be between 2 and 255 characters")
    private String name;

    private Boolean isActive;

    @Valid
    private List<AttributeValueUpdate> values;

    /**
     * Nested DTO for updating attribute values
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttributeValueUpdate {

        @NotBlank(message = "Attribute value UUID is required")
        private String uuid;

        private String value;

        private Boolean isActive;
    }
}
