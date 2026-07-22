package org.psint.beyosclothing.modules.pos.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.pos.service.PosProductSearchService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class PosSearchCacheWarmup {

    private final PosProductSearchService searchService;

    @Value("${app.pos.search.warmup.queries:}")
    private List<String> warmupQueries;

    @Value("${app.pos.search.warmup.max-queries:10}")
    private int maxQueries;

    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void warmup() {
        if (warmupQueries == null || warmupQueries.isEmpty()) {
            log.info("No warmup queries configured for POS product search");
            return;
        }

        int limit = Math.min(warmupQueries.size(), maxQueries);
        log.info("Starting POS product search cache warmup for up to {} queries", limit);

        for (int i = 0; i < limit; i++) {
            String q = warmupQueries.get(i);
            try {
                searchService.searchProducts(q, 20);
                TimeUnit.MILLISECONDS.sleep(150);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("Warmup thread interrupted; stopping warmup");
                return;
            } catch (Exception e) {
                log.warn("Warmup query failed for '{}': {}", q, e.getMessage());
            }
        }

        log.info("POS product search cache warmup completed ({} queries)", limit);
    }
}
