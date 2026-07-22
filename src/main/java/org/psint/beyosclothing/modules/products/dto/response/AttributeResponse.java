package org.psint.beyosclothing.modules.products.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for product attribute with its values
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttributeResponse {

    private String uuid; // Unique identifier for the attribute

    private String name; // Attribute name (e.g., "Size", "Color")

    private Boolean isActive; // Whether the attribute is active

    private LocalDateTime dateCreated; // When the attribute was created

    private LocalDateTime dateUpdated; // When the attribute was last updated

    private List<AttributeValueResponse> values; // List of attribute values

    /**
     * Nested DTO for attribute values
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttributeValueResponse {

        private String uuid; // Unique identifier for the attribute value

        private String value; // The actual value (e.g., "Small", "Red")

        private Boolean isActive; // Whether this value is active

        private LocalDateTime dateCreated; // When the value was created

        private LocalDateTime dateUpdated; // When the value was last updated
    }
}

