package org.psint.beyosclothing.modules.products.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Product Link Request DTO
 * Used for creating product links (Cross-sell, Upsell, Related)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductLinkRequest {

    @NotBlank(message = "Linked product UUID is required")
    private String linkedProductUuid;

    @NotBlank(message = "Link type is required")
    private String linkType; // UPSELL, CROSSSELL, RELATED
}

