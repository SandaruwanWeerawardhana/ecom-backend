package org.psint.beyosclothing.modules.products.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductGalleryRequest {
    private String mediaName; // Image name from upload API
    private Integer sortOrder;
    private String altText;
    private String mimeType; // e.g., "image/png", "image/jpeg", "image/webp"
}

