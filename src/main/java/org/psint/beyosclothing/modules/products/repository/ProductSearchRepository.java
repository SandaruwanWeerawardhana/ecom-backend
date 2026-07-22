package org.psint.beyosclothing.modules.products.repository;

import org.psint.beyosclothing.modules.products.dto.document.ProductSearchDocument;

import java.util.List;

/**
 * Product Search Repository Interface
 * Handles Elasticsearch operations for product search
 */
public interface ProductSearchRepository {

    /**
     * Real-time search products by query
     * Uses multi-match with fuzzy matching for spelling tolerance
     *
     * @param query Search query from user
     * @param limit Maximum number of results
     * @return List of matching product documents
     */
    List<ProductSearchDocument> search(String query, int limit);

    /**
     * Search with autocomplete/prefix matching
     * Better for "as-you-type" search experience
     *
     * @param prefix Search prefix
     * @param limit Maximum number of results
     * @return List of matching product documents
     */
    List<ProductSearchDocument> searchPrefix(String prefix, int limit);
}
