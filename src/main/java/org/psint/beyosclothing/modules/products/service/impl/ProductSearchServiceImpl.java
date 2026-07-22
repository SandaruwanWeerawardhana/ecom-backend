package org.psint.beyosclothing.modules.products.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.products.dto.document.ProductSearchDocument;
import org.psint.beyosclothing.modules.products.dto.response.SearchResponse;
import org.psint.beyosclothing.modules.products.entity.Product;
import org.psint.beyosclothing.modules.products.repository.ProductRepository;
import org.psint.beyosclothing.modules.products.repository.ProductSearchRepository;
import org.psint.beyosclothing.modules.products.service.ProductSearchService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Product Search Service Implementation
 * Handles business logic for real-time product search with database fallback
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductSearchServiceImpl implements ProductSearchService {

    private final ProductSearchRepository productSearchRepository;
    private final ProductRepository productRepository;

    private static final int DEFAULT_SEARCH_LIMIT = 10;
    private static final int MAX_SEARCH_LIMIT = 50;

    @Override
    public List<SearchResponse> searchProducts(String query, Integer limit) {
        if (query == null || query.trim().isEmpty()) {
            log.debug("Empty search query received, returning empty results");
            return List.of();
        }

        // Validate and set limit
        int searchLimit = validateLimit(limit);

        log.info("Searching products: query='{}', limit={}", query, searchLimit);

        try {
            // Try Elasticsearch search first
            List<ProductSearchDocument> documents = productSearchRepository.search(query, searchLimit);

            // Convert to lightweight response DTOs
            List<SearchResponse> results = documents.stream()
                    .filter(Objects::nonNull)  // Filter out null documents
                    .map(this::mapToSearchResponse)
                    .filter(Objects::nonNull)  // Filter out null responses
                    .toList();

            log.info("Elasticsearch search completed: query='{}', results={}", query, results.size());

            // If Elasticsearch returns no results, fall back to database search
            if (results.isEmpty()) {
                log.warn("Elasticsearch returned no results for '{}', falling back to database search", query);
                results = searchFromDatabase(query, searchLimit);
            }

            return results;

        } catch (Exception e) {
            log.error("Elasticsearch search failed for '{}', falling back to database: {}", query, e.getMessage());
            return searchFromDatabase(query, searchLimit);
        }
    }

    @Override
    public List<SearchResponse> autocomplete(String prefix, Integer limit) {
        if (prefix == null || prefix.trim().isEmpty()) {
            return List.of();
        }

        int searchLimit = validateLimit(limit);

        log.debug("Autocomplete search: prefix='{}', limit={}", prefix, searchLimit);

        try {
            List<ProductSearchDocument> documents = productSearchRepository.searchPrefix(prefix, searchLimit);

            List<SearchResponse> results = documents.stream()
                    .filter(Objects::nonNull)  // Filter out null documents
                    .map(this::mapToSearchResponse)
                    .filter(Objects::nonNull)  // Filter out null responses
                    .toList();

            // If Elasticsearch returns no results, fall back to database search
            if (results.isEmpty()) {
                log.warn("Elasticsearch autocomplete returned no results, falling back to database search");
                results = searchFromDatabase(prefix, searchLimit);
            }

            return results;

        } catch (Exception e) {
            log.error("Elasticsearch autocomplete failed, falling back to database: {}", e.getMessage());
            return searchFromDatabase(prefix, searchLimit);
        }
    }

    /**
     */
    private int validateLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_SEARCH_LIMIT;
        }
        return Math.min(limit, MAX_SEARCH_LIMIT);
    }

    /**
     * Map Elasticsearch document to lightweight search response
     */
    private SearchResponse mapToSearchResponse(ProductSearchDocument doc) {
        return SearchResponse.builder()
                .uuid(doc.getUuid())
                .title(doc.getTitle())
                .slug(doc.getSlug())
                .build();
    }

    /**
     * Database fallback search for products by title
     */
    private List<SearchResponse> searchFromDatabase(String query, int limit) {
        log.info("Performing database search: query='{}', limit={}", query, limit);

        try {
            List<Product> products = productRepository.searchByTitle(query);

            List<SearchResponse> results = products.stream()
                    .limit(limit)
                    .map(this::mapProductToSearchResponse)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            log.info("Database search completed: query='{}', results={}", query, results.size());
            return results;

        } catch (Exception e) {
            log.error("Database search failed: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Map Product entity to search response
     */
    private SearchResponse mapProductToSearchResponse(Product product) {
        if (product == null) {
            return null;
        }

        return SearchResponse.builder()
                .uuid(product.getUuid())
                .title(product.getTitle())
                .slug(product.getSlug())
                .build();
    }
}


