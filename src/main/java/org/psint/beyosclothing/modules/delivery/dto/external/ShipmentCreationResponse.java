package org.psint.beyosclothing.modules.delivery.dto.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Response DTO for shipment creation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentCreationResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private String requestId;
    private Boolean success;
    private Long shipmentId;
    private String shipmentUuid;
    private String errorMessage;
}

