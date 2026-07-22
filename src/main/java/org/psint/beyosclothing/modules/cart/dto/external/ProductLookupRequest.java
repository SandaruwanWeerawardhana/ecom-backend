package org.psint.beyosclothing.modules.cart.dto.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Request to lookup product by UUID
 * Sent to Product module via RabbitMQ
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductLookupRequest implements Serializable {
    private String productUuid;
    private String variantUuid; // Optional - for variant lookup
    private String requestId;
}

