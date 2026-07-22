package org.psint.beyosclothing.modules.pos.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.pos.dto.document.PosProductDocument;
import org.psint.beyosclothing.modules.pos.entity.PosProductCacheEntity;
import org.psint.beyosclothing.modules.pos.repository.PosProductCacheRepository;
import org.psint.beyosclothing.modules.pos.repository.PosProductSearchRepository;
import org.psint.beyosclothing.modules.pos.service.PosProductSearchService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PosProductSearchServiceImpl implements PosProductSearchService {

    private final PosProductSearchRepository searchRepository;
    private final PosProductCacheRepository cacheRepository;

    private static final int MAX_LIMIT = 20;
    private static final String CACHE_KEY_PREFIX = "pos:product:search:";

    @Override
    @Cacheable(value = "pos-products", key = "T(org.psint.beyosclothing.modules.pos.service.impl.PosProductSearchServiceImpl).buildCacheKey(#query,#limit)")
    public List<PosProductDocument> searchProducts(String query, int limit) {
        int effectiveLimit = Math.min(limit <= 0 ? MAX_LIMIT : limit, MAX_LIMIT);

        try {
            List<PosProductDocument> results = searchRepository.search(query, effectiveLimit);
            if (results == null) results = List.of();

            List<PosProductDocument> filtered = filterActiveAndInStock(results).stream().limit(effectiveLimit).toList();
            log.debug("Elasticsearch returned {} results for query={}", filtered.size(), query);
            return filtered;
        } catch (Exception esEx) {
            log.error("Elasticsearch search failed, falling back to MySQL: {}", esEx.getMessage());
            try {
                List<PosProductCacheEntity> fallback = cacheRepository.findAll().stream()
                        .filter(e -> e.getIsActive() != null && e.getIsActive())
                        .filter(e -> (e.getTitle() != null && e.getTitle().toLowerCase().contains(query.toLowerCase()))
                                || (e.getSku() != null && e.getSku().toLowerCase().contains(query.toLowerCase())))
                        .limit(effectiveLimit)
                        .toList();

                return fallback.stream().map(this::mapEntityToDoc).toList();
            } catch (Exception sqlEx) {
                log.error("MySQL fallback search failed: {}", sqlEx.getMessage());
                return List.of();
            }
        }
    }

    private List<PosProductDocument> filterActiveAndInStock(List<PosProductDocument> list) {
        return list.stream()
                .filter(p -> p.getIsActive() != null && p.getIsActive())
                .filter(p -> p.getStockAvailable() == null || p.getStockAvailable() > 0)
                .toList();
    }

    public static String buildCacheKey(String query, int limit) {
        String q = query == null ? "" : query.trim().toLowerCase();
        return CACHE_KEY_PREFIX + q + ":" + limit;
    }

    private PosProductDocument mapEntityToDoc(PosProductCacheEntity e) {
        PosProductDocument d = new PosProductDocument();
        if (e.getProductId() != null) d.setId(String.valueOf(e.getProductId()));
        d.setProductId(e.getProductId());
        d.setUuid(e.getUuid());
        d.setTitle(e.getTitle());
        d.setSku(e.getSku());
        d.setDescription(null);
        d.setPrice(e.getShowcasePrice());
        d.setSalePrice(e.getSalePrice());
        d.setStockAvailable(e.getStockAvailable() != null ? e.getStockAvailable().longValue() : 0L);
        d.setThumbnailUrl(e.getThumbnailUrl());
        d.setCategory(null);
        d.setTags(null);
        d.setIsActive(e.getIsActive());
        return d;
    }
}
