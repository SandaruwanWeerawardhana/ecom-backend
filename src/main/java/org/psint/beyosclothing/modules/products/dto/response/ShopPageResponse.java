package org.psint.beyosclothing.modules.products.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.psint.beyosclothing.common.dto.PageResponse;

/**
 * Shop Page Response DTO
 * Complete response for shop page including products and filter metadata
 *
 * @author Beyos Development Team
 * @version 1.0
 * @since 2026-02-18
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopPageResponse {

    private PageResponse<ShopProductResponse> products;
    private ShopFilterMetadata filterMetadata;
}

