package org.psint.beyosclothing.modules.pos.search;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;

import static co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders.*;

public final class PosProductQueryBuilder {

    private static final String FUZZINESS = "2"; // Levenshtein edit distance = 2

    private PosProductQueryBuilder() {}

    public static Query build(String rawQuery) {
        if (rawQuery == null || rawQuery.trim().isEmpty()) {
            return Query.of(q -> q.matchAll(m -> m));
        }

        String q = rawQuery.trim();
        boolean isPhrase = q.startsWith("\"") && q.endsWith("\"");
        boolean hasWildcard = q.contains("*");
        boolean hasBoolean = q.toUpperCase().contains(" AND ") || q.toUpperCase().contains(" OR ");

        return Query.of(qb -> qb.bool(b -> {
            b.filter(term(t -> t.field("isActive").value(true)));

            // Exact / high-priority SKU match (boost 3)
            b.should(match(m -> m.field("sku").query(q).boost(3.0f)));

            // Title (boost 2) with fuzzy/support for phrase/wildcard/boolean
            if (isPhrase) {
                String phrase = q.substring(1, q.length() - 1);
                b.should(matchPhrase(mp -> mp.field("title").query(phrase).boost(2.0f)));
            } else if (hasWildcard) {
                b.should(wildcard(w -> w.field("title").value(q).boost(2.0f)));
            } else if (hasBoolean) {
                b.should(multiMatch(mm -> mm.query(q).fields("title^2").operator(co.elastic.clients.elasticsearch._types.query_dsl.Operator.And).fuzziness(FUZZINESS)));
            } else {
                b.should(multiMatch(mm -> mm.query(q).fields("title^2").fuzziness(FUZZINESS)));
            }

            // Tags (boost 1.5)
            b.should(multiMatch(mm -> mm.query(q).fields("tags^1.5").fuzziness(FUZZINESS)));

            // Description (boost 1)
            b.should(multiMatch(mm -> mm.query(q).fields("description").fuzziness(FUZZINESS)));

            b.minimumShouldMatch("1");
            return b;
        }));
    }
}
