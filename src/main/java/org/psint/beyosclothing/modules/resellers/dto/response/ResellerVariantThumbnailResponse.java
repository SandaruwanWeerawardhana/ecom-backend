package org.psint.beyosclothing.modules.resellers.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Reseller Variant Thumbnail Response
 * Mirrors ImageThumbnailUrlResponse from Product module
 * without creating a cross-module dependency
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Variant thumbnail URL response for reseller portal")
public class ResellerVariantThumbnailResponse {

    @Schema(description = "Variant thumbnail URL", example = "https://media.beyosclothing.com/products/abc123/thumb.jpg")
    private String thumbnailUrl;
}

