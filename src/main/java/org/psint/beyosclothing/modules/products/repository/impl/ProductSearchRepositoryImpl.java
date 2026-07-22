package org.psint.beyosclothing.modules.products.repository.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.products.dto.document.ProductSearchDocument;
import org.psint.beyosclothing.modules.products.repository.ProductSearchRepository;
import org.psint.beyosclothing.modules.products.search.ProductSearchQueryBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;

/**
 * Product Search Repository Implementation
 * High-performance Elasticsearch queries for real-time product search
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class ProductSearchRepositoryImpl implements ProductSearchRepository {

    private final ElasticsearchClient elasticsearchClient;

    @Value("${app.elasticsearch.indices.products:products_read}")
    private String indexAlias;

    @Override
    public List<ProductSearchDocument> search(String query, int limit) {
        if (query == null || query.trim().isEmpty()) {
            return List.of();
        }

        try {
            log.debug("Elasticsearch search: query='{}', limit={}, index='{}'", query, limit, indexAlias);

            SearchResponse<ProductSearchDocument> response = elasticsearchClient.search(s -> s
                    .index(indexAlias)
                    .query(ProductSearchQueryBuilder.build(query.trim()))
                    .size(limit)
                    // Sort by relevance score (default) and then by date
                    .sort(sort -> sort.score(sc -> sc.order(SortOrder.Desc)))
                    .sort(sort -> sort.field(f -> f.field("date_created").order(SortOrder.Desc)))
                    // Only return required fields for performance
                    .source(src -> src.filter(f -> f
                            .includes("product_id", "uuid", "title", "slug", "thumbnail_url",
                                    "showcase_price", "sale_price", "category_name", "featured")
                    )),
                    ProductSearchDocument.class
            );

            List<ProductSearchDocument> results = response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .toList();

            log.debug("Elasticsearch search returned {} results in {}ms",
                    results.size(), response.took());

            return results;

        } catch (Exception e) {
            log.error("Elasticsearch search failed for query: '{}', error: {}", query, e.getMessage(), e);
            // Return empty list instead of throwing exception for better UX
            // The frontend can handle empty results gracefully
            return List.of();
        }
    }

    @Override
    public List<ProductSearchDocument> searchPrefix(String prefix, int limit) {
        if (prefix == null || prefix.trim().isEmpty()) {
            return List.of();
        }

        try {
            log.debug("Elasticsearch prefix search: prefix='{}', limit={}", prefix, limit);

            SearchResponse<ProductSearchDocument> response = elasticsearchClient.search(s -> s
                    .index(indexAlias)
                    .query(ProductSearchQueryBuilder.buildPrefixQuery(prefix.trim()))
                    .size(limit)
                    .sort(sort -> sort.score(sc -> sc.order(SortOrder.Desc)))
                    .sort(sort -> sort.field(f -> f.field("date_created").order(SortOrder.Desc)))
                    .source(src -> src.filter(f -> f
                            .includes("product_id", "uuid", "title", "slug", "thumbnail_url",
                                    "showcase_price", "sale_price", "category_name", "featured")
                    )),
                    ProductSearchDocument.class
            );

            return response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(doc -> doc != null)  // Filter out null sources
                    .toList();

        } catch (Exception e) {
            log.error("Elasticsearch prefix search failed for prefix: '{}', error: {}", prefix, e.getMessage(), e);
            return List.of();
        }
    }
}
