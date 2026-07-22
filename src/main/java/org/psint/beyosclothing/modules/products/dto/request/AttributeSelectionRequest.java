package org.psint.beyosclothing.modules.products.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for attribute selection during product creation
 * Supports both existing and new attributes with their values
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttributeSelectionRequest {

    /**
     * UUID of existing attribute (null if new)
     */
    private String attributeUuid;

    /**
     * Attribute name (required if isNew = true)
     */
    private String attributeName;

    /**
     * Flag indicating if this is a new attribute
     */
    private Boolean isNew = false;

    /**
     * Selected values for this attribute
     */
    @NotEmpty(message = "At least one attribute value must be selected")
    @Valid
    private List<AttributeValueSelectionRequest> values;
}

