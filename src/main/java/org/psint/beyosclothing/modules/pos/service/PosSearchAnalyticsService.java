package org.psint.beyosclothing.modules.pos.service;

import org.psint.beyosclothing.modules.pos.dto.document.PosSearchAnalyticsDocument;

import java.time.LocalDate;
import java.util.Map;
import java.util.List;

public interface PosSearchAnalyticsService {
    void logSearch(PosSearchAnalyticsDocument doc);
    Map<String, Long> topSearchedProducts(LocalDate from, LocalDate to, int topN);
    List<String> zeroResultSearches(LocalDate from, LocalDate to);
    double searchToConversionRate(LocalDate from, LocalDate to);
}
