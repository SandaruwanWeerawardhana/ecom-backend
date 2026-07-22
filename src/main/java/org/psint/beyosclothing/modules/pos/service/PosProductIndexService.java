package org.psint.beyosclothing.modules.pos.service;

import org.psint.beyosclothing.modules.pos.dto.document.PosProductDocument;

public interface PosProductIndexService {
    void indexProduct(PosProductDocument doc);
    void deleteProduct(String id);
}
