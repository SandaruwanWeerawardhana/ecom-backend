package org.psint.beyosclothing.modules.pos.service;

import org.psint.beyosclothing.modules.pos.dto.response.ImageThumbnailUrlPosResponse;
import org.psint.beyosclothing.modules.pos.dto.response.PosProductListResponse;
import org.psint.beyosclothing.modules.pos.dto.response.PosProductPopupResponse;
import org.psint.beyosclothing.modules.pos.dto.response.PosProductSearchResponse;
import org.psint.beyosclothing.modules.products.dto.response.ImageThumbnailUrlResponse;

import java.util.List;

public interface PosProductService {
    List<PosProductSearchResponse> searchProducts(String query, Integer limit);

    PosProductSearchResponse getProductForPos(Long productId);

    PosProductListResponse getAllActiveProducts(Integer page, Integer size);

    PosProductPopupResponse getProductForPosByUuid(String productUuid);

    ImageThumbnailUrlPosResponse getVariantGalleryImages(String uuid);
}
