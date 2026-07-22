package org.psint.beyosclothing.modules.products.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductMetaRequest {
    private String metaTitle;
    private String metaDescription;
    private String canonicalUrl;
    private String metaKeywords;
    private String ogTitle;
    private String ogDescription;
    private String ogImage;
    private String twitterCard;
    private String jsonLd;
    private Boolean robotIndex = true;
}

