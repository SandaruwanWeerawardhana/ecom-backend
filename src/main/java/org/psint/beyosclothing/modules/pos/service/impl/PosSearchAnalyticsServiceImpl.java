package org.psint.beyosclothing.modules.pos.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.JsonData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.pos.dto.document.PosSearchAnalyticsDocument;
import org.psint.beyosclothing.modules.pos.service.PosSearchAnalyticsService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PosSearchAnalyticsServiceImpl implements PosSearchAnalyticsService {

    private final ElasticsearchClient esClient;
    private static final String INDEX = "pos_search_analytics";
    private static final String TIMESTAMP_FIELD = "timestamp";
    private static final String TOP_PRODUCTS_AGG = "top_products";
    private static final String ZERO_TERMS_AGG = "zero_terms";

    @Override
    @Async
    public void logSearch(PosSearchAnalyticsDocument doc) {
        try {
            ensureIndexExists();
            IndexRequest<PosSearchAnalyticsDocument> req = IndexRequest.of(i -> i
                    .index(INDEX)
                    .id(null)
                    .document(doc)
            );
            esClient.index(req);
        } catch (IOException e) {
            log.warn("Failed to index search analytics: {}", e.getMessage());
        }
    }

    @Override
    public Map<String, Long> topSearchedProducts(LocalDate from, LocalDate to, int topN) {
        try {
            long fromMillis = from.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
            long toMillis = to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() - 1;

            SearchResponse<Void> resp = esClient.search(s -> s
                            .index(INDEX)
                            .size(0)
                            .query(q -> q
                                    .range(r -> r
                                            .field(TIMESTAMP_FIELD)
                                            .gte(JsonData.of(fromMillis))
                                            .lte(JsonData.of(toMillis))
                                    )
                            )
                            .aggregations(TOP_PRODUCTS_AGG, a -> a
                                    .terms(t -> t.field("selectedProductId.keyword").size(topN))
                            ),
                    Void.class
            );

            Map<String, Long> result = new HashMap<>();
            if (resp.aggregations() != null && resp.aggregations().containsKey(TOP_PRODUCTS_AGG)) {
                var agg = resp.aggregations().get(TOP_PRODUCTS_AGG).sterms();
                agg.buckets().array().forEach(b -> result.put(b.key().stringValue(), b.docCount()));
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to fetch top searched products: {}", e.getMessage());
            return Map.of();
        }
    }

    @Override
    public List<String> zeroResultSearches(LocalDate from, LocalDate to) {
        try {
            long fromMillis = from.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
            long toMillis = to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() - 1;

            SearchResponse<Void> resp = esClient.search(s -> s
                            .index(INDEX)
                            .size(0)
                            .query(q -> q
                                    .bool(b -> b
                                            .filter(r -> r.range(rr -> rr.field(TIMESTAMP_FIELD)
                                                    .gte(JsonData.of(fromMillis))
                                                    .lte(JsonData.of(toMillis))
                                            ))
                                            .filter(r -> r.term(tt -> tt.field("resultsCount").value(0)))
                                    )
                            )
                            .aggregations(ZERO_TERMS_AGG, a -> a
                                    .terms(t -> t.field("searchTerm.keyword").size(1000))
                            ),
                    Void.class
            );

            if (resp.aggregations() != null && resp.aggregations().containsKey(ZERO_TERMS_AGG)) {
                var agg = resp.aggregations().get(ZERO_TERMS_AGG).sterms();
                return agg.buckets().array().stream().map(b -> b.key().stringValue()).toList();
            }
            return List.of();
        } catch (Exception e) {
            log.warn("Failed to fetch zero-result searches: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public double searchToConversionRate(LocalDate from, LocalDate to) {
        try {
            long fromMillis = from.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
            long toMillis = to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() - 1;

            SearchResponse<Void> totalResp = esClient.search(s -> s
                            .index(INDEX)
                            .size(0)
                            .query(q -> q.range(r -> r.field(TIMESTAMP_FIELD)
                                    .gte(JsonData.of(fromMillis))
                                    .lte(JsonData.of(toMillis))
                            )),
                    Void.class
            );

            long total = totalResp.hits().total() != null ? totalResp.hits().total().value() : 0L;

            SearchResponse<Void> convResp = esClient.search(s -> s
                            .index(INDEX)
                            .size(0)
                            .query(q -> q.bool(b -> b
                                    .filter(r -> r.range(rr -> rr.field(TIMESTAMP_FIELD)
                                            .gte(JsonData.of(fromMillis))
                                            .lte(JsonData.of(toMillis))
                                    ))
                                    .filter(r -> r.exists(ex -> ex.field("selectedProductId")))
                            )),
                    Void.class
            );

            long conv = convResp.hits().total() != null ? convResp.hits().total().value() : 0L;

            if (total == 0) return 0.0;
            return (double) conv / (double) total;
        } catch (Exception e) {
            log.warn("Failed to compute search-to-conversion rate: {}", e.getMessage());
            return 0.0;
        }
    }

    private void ensureIndexExists() throws IOException {
        try {
            boolean exists = esClient.indices().exists(e -> e.index(INDEX)).value();
            if (exists) return;
        } catch (Exception ex) {
            log.warn("Could not check index existence: {}", ex.getMessage());
        }

        esClient.indices().create(c -> c
                .index(INDEX)
                .mappings(m -> m
                        .properties("searchTerm", p -> p.text(t -> t))
                        .properties(TIMESTAMP_FIELD, p -> p.date(d -> d))
                        .properties("terminalId", p -> p.keyword(k -> k))
                        .properties("cashierId", p -> p.keyword(k -> k))
                        .properties("resultsCount", p -> p.integer(i -> i))
                        .properties("selectedProductId", p -> p.keyword(k -> k))
                )
        );
    }
}
