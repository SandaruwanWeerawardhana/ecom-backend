package org.psint.beyosclothing.modules.pos.repository;

import org.psint.beyosclothing.modules.pos.dto.document.PosProductDocument;

import java.util.List;

public interface PosProductSearchRepository {
    List<PosProductDocument> search(String query, int limit);
    List<String> suggest(String prefix, int limit);
}
