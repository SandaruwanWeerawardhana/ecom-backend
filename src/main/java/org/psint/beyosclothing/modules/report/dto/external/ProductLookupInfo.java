package org.psint.beyosclothing.modules.report.dto.external;

/**
 * Product info the report module resolves from the Product module over RabbitMQ.
 * The fields intentionally stay read-only transport data for report enrichment.
 */
public record ProductLookupInfo(
        Long productId,
        String title,
        String type,      // SIMPLE | VARIABLE
        Long categoryId,
        String category,
        String image,
        boolean found
) {
}
