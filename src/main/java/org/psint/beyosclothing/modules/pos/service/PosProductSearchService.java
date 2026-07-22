package org.psint.beyosclothing.modules.pos.service;

import org.psint.beyosclothing.modules.pos.dto.document.PosProductDocument;

import java.util.List;

public interface PosProductSearchService {
    List<PosProductDocument> searchProducts(String query, int limit);
}
