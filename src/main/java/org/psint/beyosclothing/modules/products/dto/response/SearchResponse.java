package org.psint.beyosclothing.modules.products.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight Search Response for real-time product search
 * Returns minimal fields for fast UI rendering during typing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResponse {

    private String uuid;
    private String title;
    private String slug;
}
