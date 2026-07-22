package org.psint.beyosclothing.modules.report.dto.external;

/**
 * Minimal variant info the report module resolves from the Product module over
 * RabbitMQ. {@code attributeSummary} (e.g. "Size: M, Color: Black") is parsed
 * locally into colour/size for the response.
 */
public record VariantLookupInfo(
        Long variantId,
        String sku,
        String attributeSummary
) {
}
