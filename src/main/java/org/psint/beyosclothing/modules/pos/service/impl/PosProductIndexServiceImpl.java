package org.psint.beyosclothing.modules.pos.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.pos.dto.document.PosProductDocument;
import org.psint.beyosclothing.modules.pos.service.PosProductIndexService;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@RequiredArgsConstructor
@Slf4j
public class PosProductIndexServiceImpl implements PosProductIndexService {

    private final ElasticsearchClient elasticsearchClient;
    private static final String INDEX = "pos_products";

    @Override
    public void indexProduct(PosProductDocument doc) {
        try {
            ensureIndexExists();

            IndexRequest<PosProductDocument> request = IndexRequest.of(i -> i
                    .index(INDEX)
                    .id(doc.getId())
                    .document(doc)
            );
            elasticsearchClient.index(request);
            log.info("Indexed product in Elasticsearch pos_products: {}", doc.getId());
        } catch (IOException e) {
            log.error("Failed to index product {} in Elasticsearch", doc.getId(), e);
        }
    }

    @Override
    public void deleteProduct(String id) {
        try {
            elasticsearchClient.delete(d -> d.index(INDEX).id(id));
            log.info("Deleted product from Elasticsearch pos_products: {}", id);
        } catch (IOException e) {
            log.error("Failed to delete product {} from Elasticsearch", id, e);
        }
    }

    private void ensureIndexExists() throws IOException {
        try {
            boolean exists = elasticsearchClient.indices().exists(e -> e.index(INDEX)).value();
            if (exists) return;
        } catch (Exception ex) {
            // If check fails, log and continue - will attempt to create
            log.warn("Could not check if index exists: {}", ex.getMessage());
        }

        // Create index with mappings suitable for product search
        try {
            elasticsearchClient.indices().create(c -> c
                    .index(INDEX)
                    .mappings(m -> m
                            .properties("productId", p -> p.long_(l -> l))
                            .properties("uuid", p -> p.keyword(k -> k))
                            .properties("sku", p -> p.keyword(k -> k))
                            .properties("title", p -> p.text(t -> t
                                    .fields("keyword", f -> f.keyword(k -> k))
                            ))
                            .properties("description", p -> p.text(t -> t
                                    .fields("keyword", f -> f.keyword(k -> k))
                            ))
                            .properties("price", p -> p.double_(d -> d))
                            .properties("salePrice", p -> p.double_(d -> d))
                            .properties("stockAvailable", p -> p.long_(l -> l))
                            .properties("thumbnailUrl", p -> p.keyword(k -> k))
                            .properties("category", p -> p.keyword(k -> k))
                            .properties("tags", p -> p.text(t -> t))
                            .properties("isActive", p -> p.boolean_(b -> b))
                    )
            );

            log.info("Created Elasticsearch index '{}' with mappings", INDEX);
        } catch (IOException e) {
            log.error("Failed to create Elasticsearch index {}", INDEX, e);
            throw e;
        }
    }
}
