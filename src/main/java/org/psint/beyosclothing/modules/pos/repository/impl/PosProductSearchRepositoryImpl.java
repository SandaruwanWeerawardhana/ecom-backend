package org.psint.beyosclothing.modules.pos.repository.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import lombok.RequiredArgsConstructor;
import org.psint.beyosclothing.modules.pos.dto.document.PosProductDocument;
import org.psint.beyosclothing.modules.pos.entity.PosProductCacheEntity;
import org.psint.beyosclothing.modules.pos.repository.PosProductCacheRepository;
import org.psint.beyosclothing.modules.pos.repository.PosProductSearchRepository;
import org.psint.beyosclothing.modules.pos.search.PosProductQueryBuilder;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.util.List;

import static co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders.*;

@Repository
@RequiredArgsConstructor
public class PosProductSearchRepositoryImpl implements PosProductSearchRepository {

    private final ElasticsearchClient elasticsearchClient;
    private final PosProductCacheRepository cacheRepository;
    private static final String INDEX = "pos_products";

    @Override
    public List<PosProductDocument> search(String query, int limit) {
        if (query == null || query.trim().isEmpty()) return List.of();
        String q = query.trim();

        try {
            SearchResponse<PosProductDocument> response = elasticsearchClient.search(s -> s
                    .index(INDEX)
                    .size(limit)
                    .query(PosProductQueryBuilder.build(q)),
                    PosProductDocument.class
            );

            return response.hits().hits().stream()
                    .map(Hit::source)
                    .toList();
        } catch (Exception e) {
            // Fail-fast and fallback to MySQL cache
            try {
                String lower = q.toLowerCase();
                return cacheRepository.findAll().stream()
                        .filter(ent -> ent.getIsActive() != null && ent.getIsActive())
                        .filter(ent -> (ent.getTitle() != null && ent.getTitle().toLowerCase().contains(lower))
                                || (ent.getSku() != null && ent.getSku().toLowerCase().contains(lower)))
                        .limit(limit)
                        .map(this::mapEntityToDoc)
                        .toList();
            } catch (Exception ex) {
                return List.of();
            }
        }
    }

    @Override
    public List<String> suggest(String prefix, int limit) {
        try {
            SearchResponse<PosProductDocument> response = elasticsearchClient.search(s -> s
                    .index(INDEX)
                    .size(limit)
                    .query(q -> q
                            .bool(b -> b
                                    .should(multiMatch(mm -> mm
                                            .query(prefix)
                                            .fields("title^3", "sku^2", "description")
                                            .type(co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType.PhrasePrefix)
                                    ))
                            )
                    ),
                    PosProductDocument.class
            );

            return response.hits().hits().stream()
                    .map(Hit::source)
                    .map(doc -> {
                        String title = doc.getTitle();
                        if (title != null) return title;
                        String sku = doc.getSku();
                        return sku != null ? sku : "";
                    })
                    .toList();
        } catch (IOException e) {
            return cacheRepository.findAll().stream()
                    .map(PosProductCacheEntity::getTitle)
                    .filter(t -> t != null && t.toLowerCase().startsWith(prefix.toLowerCase()))
                    .limit(limit)
                    .toList();
        }
    }

    private PosProductDocument mapEntityToDoc(PosProductCacheEntity e) {
        PosProductDocument doc = new PosProductDocument();
        if (e.getProductId() != null) doc.setId(String.valueOf(e.getProductId()));
        doc.setProductId(e.getProductId());
        doc.setUuid(e.getUuid());
        doc.setSku(e.getSku());
        doc.setTitle(e.getTitle());
        doc.setDescription(null);
        doc.setPrice(e.getShowcasePrice());
        doc.setSalePrice(e.getSalePrice());
        doc.setStockAvailable(e.getStockAvailable() != null ? e.getStockAvailable().longValue() : 0L);
        doc.setThumbnailUrl(e.getThumbnailUrl());
        doc.setCategory(null);
        doc.setTags(null);
        doc.setIsActive(e.getIsActive());
        return doc;
    }
}
