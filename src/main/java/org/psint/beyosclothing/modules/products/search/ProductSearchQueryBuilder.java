package org.psint.beyosclothing.modules.products.search;

import co.elastic.clients.elasticsearch._types.query_dsl.*;

/**
 * Elasticsearch Query Builder for Product Search
 * Optimized for real-time search with fuzzy matching and relevance scoring
 */
public class ProductSearchQueryBuilder {

    /**
     * Build optimized search query for real-time product search
     * Features:
     * - Multi-match with field boosting (title > description > sku)
     * - Fuzzy matching for spelling tolerance
     * - Phrase prefix for partial typing support
     * - Only search published and active products
     *
     * @param searchQuery User's search input
     * @return Elasticsearch Query
     */
    public static Query build(String searchQuery) {
        return BoolQuery.of(b -> b
                // Must match search query
                .must(multiMatchQuery(searchQuery))

                // Filter only active and published products
                .filter(f -> f.term(t -> t.field("is_publish").value(true)))
                .filter(f -> f.term(t -> t.field("is_active").value(true)))
        )._toQuery();
    }

    /**
     * Multi-match query with field boosting and fuzzy matching
     * Strategy:
     * - Use best_fields type for highest relevance score
     * - Boost title 3x (most important)
     * - Boost SKU 2x (product code searches)
     * - Boost short_description 1.5x
     * - Description has default weight
     * - Fuzziness AUTO for spelling tolerance (1-2 character edits)
     * - Prefix length 2 to avoid too many fuzzy matches
     */
    private static Query multiMatchQuery(String query) {
        return MultiMatchQuery.of(mm -> mm
                .query(query)
                .fields("title^3", "sku^2", "short_description^1.5", "description")
                .type(TextQueryType.BestFields)
                .fuzziness("AUTO")
                .prefixLength(2)
                .operator(Operator.Or)
                .tieBreaker(0.3) // Consider other fields if multiple match
        )._toQuery();
    }

    /**
     * Build query for autocomplete/suggestions using phrase prefix
     * Better for "as-you-type" experience
     * Example: "iph" → matches "iPhone 14 Pro"
     */
    public static Query buildPrefixQuery(String prefix) {
        return BoolQuery.of(b -> b
                .must(m -> m.bool(bb -> bb
                        .should(s -> s.matchPhrasePrefix(mpq -> mpq
                                .field("title")
                                .query(prefix)
                                .boost(3.0f)
                        ))
                        .should(s -> s.matchPhrasePrefix(mpq -> mpq
                                .field("sku")
                                .query(prefix)
                                .boost(2.0f)
                        ))
                        .should(s -> s.matchPhrasePrefix(mpq -> mpq
                                .field("short_description")
                                .query(prefix)
                                .boost(1.5f)
                        ))
                        .minimumShouldMatch("1")
                ))
                .filter(f -> f.term(t -> t.field("is_publish").value(true)))
                .filter(f -> f.term(t -> t.field("is_active").value(true)))
        )._toQuery();
    }
}
