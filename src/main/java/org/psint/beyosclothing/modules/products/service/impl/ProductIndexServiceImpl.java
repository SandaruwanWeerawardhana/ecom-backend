package org.psint.beyosclothing.modules.products.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TextProperty;
import co.elastic.clients.elasticsearch._types.mapping.KeywordProperty;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.products.dto.document.ProductSearchDocument;
import org.psint.beyosclothing.modules.products.entity.Product;
import org.psint.beyosclothing.modules.products.repository.ProductRepository;
import org.psint.beyosclothing.modules.products.service.ProductIndexService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Product Index Service Implementation
 * Handles Elasticsearch indexing for products
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductIndexServiceImpl implements ProductIndexService {

    private final ElasticsearchClient elasticsearchClient;
    private final ProductRepository productRepository;

    @Value("${app.elasticsearch.indices.products:products_read}")
    private String indexName;

    @Override
    public void indexProduct(Product product) {
        if (product == null || !product.getIsPublish() || !product.getIsActive()) {
            log.debug("Skipping indexing for unpublished or inactive product: {}", product != null ? product.getId() : null);
            return;
        }

        try {
            ProductSearchDocument document = mapToDocument(product);

            elasticsearchClient.index(i -> i
                    .index(indexName)
                    .id(product.getId().toString())
                    .document(document)
            );

            log.info("Successfully indexed product: {} (ID: {})", product.getTitle(), product.getId());

        } catch (Exception e) {
            log.error("Failed to index product ID {}: {}", product.getId(), e.getMessage(), e);
        }
    }

    @Override
    public void updateProduct(Product product) {
        // For updates, we can use the same index operation (upsert behavior)
        indexProduct(product);
    }

    @Override
    public void deleteProduct(Long productId) {
        try {
            elasticsearchClient.delete(d -> d
                    .index(indexName)
                    .id(productId.toString())
            );

            log.info("Successfully deleted product from index: ID {}", productId);

        } catch (Exception e) {
            log.error("Failed to delete product ID {} from index: {}", productId, e.getMessage(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public int bulkIndexPublishedProducts() {
        log.info("Starting bulk indexing of published products...");

        try {
            // Fetch all published products from database
            List<Product> products = productRepository.findAllPublishedProducts();

            if (products.isEmpty()) {
                log.warn("No published products found to index");
                return 0;
            }

            log.info("Found {} published products to index", products.size());

            // Build bulk request
            BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();

            for (Product product : products) {
                ProductSearchDocument document = mapToDocument(product);

                bulkBuilder.operations(op -> op
                        .index(idx -> idx
                                .index(indexName)
                                .id(product.getId().toString())
                                .document(document)
                        )
                );
            }

            // Execute bulk request
            BulkResponse response = elasticsearchClient.bulk(bulkBuilder.build());

            // Check for errors
            if (response.errors()) {
                int errorCount = 0;
                for (BulkResponseItem item : response.items()) {
                    if (item.error() != null) {
                        log.error("Bulk indexing error for product {}: {}",
                                item.id(), item.error().reason());
                        errorCount++;
                    }
                }
                log.warn("Bulk indexing completed with {} errors", errorCount);
                return products.size() - errorCount;
            }

            log.info("Successfully bulk indexed {} products in {}ms",
                    products.size(), response.took());

            return products.size();

        } catch (Exception e) {
            log.error("Bulk indexing failed: {}", e.getMessage(), e);
            return 0;
        }
    }

    @Override
    public boolean indexExists() {
        try {
            return elasticsearchClient.indices()
                    .exists(ExistsRequest.of(e -> e.index(indexName)))
                    .value();
        } catch (Exception e) {
            log.error("Failed to check if index exists: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public void createIndex() {
        try {
            if (indexExists()) {
                log.info("Index '{}' already exists", indexName);
                return;
            }

            // Create index with optimized mappings
            Map<String, Property> properties = new HashMap<>();

            // Text fields with analyzers for search
            properties.put("title", Property.of(p -> p.text(TextProperty.of(t -> t
                    .analyzer("standard")
                    .fields("keyword", Property.of(kp -> kp.keyword(KeywordProperty.of(k -> k))))
            ))));

            properties.put("sku", Property.of(p -> p.text(TextProperty.of(t -> t
                    .analyzer("standard")
                    .fields("keyword", Property.of(kp -> kp.keyword(KeywordProperty.of(k -> k))))
            ))));

            properties.put("short_description", Property.of(p -> p.text(TextProperty.of(t -> t
                    .analyzer("standard")
            ))));

            properties.put("description", Property.of(p -> p.text(TextProperty.of(t -> t
                    .analyzer("standard")
            ))));

            // Exact match fields
            properties.put("uuid", Property.of(p -> p.keyword(KeywordProperty.of(k -> k))));
            properties.put("slug", Property.of(p -> p.keyword(KeywordProperty.of(k -> k))));
            properties.put("thumbnail_url", Property.of(p -> p.keyword(KeywordProperty.of(k -> k))));

            // Category with both analyzed and keyword for filtering
            properties.put("category_name", Property.of(p -> p.text(TextProperty.of(t -> t
                    .analyzer("standard")
                    .fields("keyword", Property.of(kp -> kp.keyword(KeywordProperty.of(k -> k))))
            ))));

            // Numeric fields
            properties.put("product_id", Property.of(p -> p.long_(l -> l)));
            properties.put("showcase_price", Property.of(p -> p.double_(d -> d)));
            properties.put("sale_price", Property.of(p -> p.double_(d -> d)));
            properties.put("date_created", Property.of(p -> p.long_(l -> l)));

            // Boolean fields
            properties.put("is_publish", Property.of(p -> p.boolean_(b -> b)));
            properties.put("is_active", Property.of(p -> p.boolean_(b -> b)));
            properties.put("featured", Property.of(p -> p.boolean_(b -> b)));

            elasticsearchClient.indices().create(CreateIndexRequest.of(c -> c
                    .index(indexName)
                    .mappings(TypeMapping.of(m -> m.properties(properties)))
                    .settings(s -> s
                            .numberOfShards("3")
                            .numberOfReplicas("1")
                            .refreshInterval(t -> t.time("1s"))
                    )
            ));

            log.info("Successfully created index: {}", indexName);

        } catch (Exception e) {
            log.error("Failed to create index: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create Elasticsearch index", e);
        }
    }

    @Override
    public void recreateIndex() {
        try {
            // Delete index if exists
            if (indexExists()) {
                elasticsearchClient.indices().delete(DeleteIndexRequest.of(d -> d.index(indexName)));
                log.info("Deleted existing index: {}", indexName);
            }

            // Create fresh index
            createIndex();

            // Bulk index all products
            bulkIndexPublishedProducts();

            log.info("Successfully recreated and populated index: {}", indexName);

        } catch (Exception e) {
            log.error("Failed to recreate index: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to recreate Elasticsearch index", e);
        }
    }

    /**
     * Map Product entity to Elasticsearch document
     */
    private ProductSearchDocument mapToDocument(Product product) {
        return ProductSearchDocument.builder()
                .productId(product.getId())
                .uuid(product.getUuid())
                .sku(product.getSku())
                .title(product.getTitle())
                .slug(product.getSlug())
                .shortDescription(product.getShortDescription())
                .description(product.getDescription())
                .thumbnailUrl(product.getThumbnailUrl())
                .showcasePrice(product.getShowcasePrice())
                .salePrice(product.getSalePrice())
                .categoryName(null) // TODO: Fetch category name if needed
                .isPublish(product.getIsPublish())
                .isActive(product.getIsActive())
                .featured(product.getFeatured())
                .dateCreated(product.getDateCreated() != null
                        ? product.getDateCreated().toEpochSecond(ZoneOffset.UTC)
                        : null)
                .build();
    }
}
