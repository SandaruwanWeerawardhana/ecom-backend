package org.psint.beyosclothing.modules.pos.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.pos.service.PosSearchAnalyticsService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class PosSearchAnalyticsAggregator {

    private final PosSearchAnalyticsService analyticsService;
    private final ElasticsearchClient esClient;
    private static final String SUMMARY_INDEX = "pos_search_analytics_summary";

    @Scheduled(cron = "0 5 0 * * ?") // run daily at 00:05
    public void aggregateDaily() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        log.info("Starting daily search analytics aggregation for {}", yesterday);

        try {
            Map<String, Long> top = analyticsService.topSearchedProducts(yesterday, yesterday, 50);
            var zero = analyticsService.zeroResultSearches(yesterday, yesterday);
            double rate = analyticsService.searchToConversionRate(yesterday, yesterday);

            // Save summary into summary index
            esClient.index(i -> i.index(SUMMARY_INDEX).document(Map.of(
                    "date", yesterday.toString(),
                    "topProducts", top,
                    "zeroSearches", zero,
                    "conversionRate", rate
            )));

            log.info("Aggregated daily search analytics for {}: top={}, zeroCount={}, rate={}", yesterday, top.size(), zero.size(), rate);
        } catch (Exception e) {
            log.warn("Failed to aggregate daily analytics: {}", e.getMessage());
        }
    }
}
