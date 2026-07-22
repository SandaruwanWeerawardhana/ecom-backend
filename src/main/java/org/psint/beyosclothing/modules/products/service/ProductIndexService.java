package org.psint.beyosclothing.modules.products.service;

import org.psint.beyosclothing.modules.products.entity.Product;

/**
 * Product Index Service
 * Handles indexing products to Elasticsearch for search functionality
 */
public interface ProductIndexService {

    /**
     * Index a single product to Elasticsearch
     *
     * @param product Product entity to index
     */
    void indexProduct(Product product);

    /**
     * Update indexed product in Elasticsearch
     *
     * @param product Product entity to update
     */
    void updateProduct(Product product);

    /**
     * Delete product from Elasticsearch index
     *
     * @param productId Product ID to delete
     */
    void deleteProduct(Long productId);

    /**
     * Bulk index all published products
     * Used for initial setup or re-indexing
     *
     * @return Number of products indexed
     */
    int bulkIndexPublishedProducts();

    /**
     * Check if Elasticsearch index exists
     *
     * @return true if index exists, false otherwise
     */
    boolean indexExists();

    /**
     * Create Elasticsearch index with optimized mappings
     */
    void createIndex();

    /**
     * Delete and recreate index (use with caution)
     */
    void recreateIndex();
}
