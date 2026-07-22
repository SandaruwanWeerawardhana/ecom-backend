package org.psint.beyosclothing.modules.products.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for attribute value selection
 * Supports both existing and new attribute values
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttributeValueSelectionRequest {

    /**
     * UUID of existing attribute value (null if new)
     */
    private String attributeValueUuid;

    /**
     * Value string (required if isNew = true)
     */
    private String value;

    /**
     * Flag indicating if this is a new attribute value
     */
    private Boolean isNew = false;
}

