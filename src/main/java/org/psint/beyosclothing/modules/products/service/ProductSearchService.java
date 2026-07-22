package org.psint.beyosclothing.modules.products.service;

import org.psint.beyosclothing.modules.products.dto.response.SearchResponse;

import java.util.List;

/**
 * Product Search Service Interface
 * Provides real-time product search functionality
 */
public interface ProductSearchService {

    /**
     * Real-time product search with fuzzy matching
     * Optimized for user typing in search box
     *
     * @param query Search query from user
     * @param limit Maximum number of results (default: 10)
     * @return List of search results with minimal fields
     */
    List<SearchResponse> searchProducts(String query, Integer limit);

    /**
     * Autocomplete search with prefix matching
     * Better for "as-you-type" suggestions
     *
     * @param prefix Search prefix
     * @param limit Maximum number of results
     * @return List of search results
     */
    List<SearchResponse> autocomplete(String prefix, Integer limit);
}
