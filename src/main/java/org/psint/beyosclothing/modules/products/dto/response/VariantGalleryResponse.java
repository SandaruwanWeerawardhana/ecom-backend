package org.psint.beyosclothing.modules.products.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VariantGalleryResponse {
    private String uuid;
    private String mediaUrl;
    private Integer sortOrder;
    private String altText;
    private String mimeType;
}

