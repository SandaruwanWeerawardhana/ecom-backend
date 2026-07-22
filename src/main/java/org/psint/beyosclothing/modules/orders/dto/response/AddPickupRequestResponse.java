package org.psint.beyosclothing.modules.orders.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for adding pickup requests
 * Contains the result and details of the pickup request submission to Koombiyo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddPickupRequestResponse {

    /**
     * Shipment UUID
     */
    private String shipmentUuid;

    /**
     * Waybill ID
     */
    private String wayBillId;

    /**
     * Vehicle type requested
     */
    private String vehicleType;

    /**
     * Pickup address
     */
    private String pickupAddress;

    /**
     * Pickup status from Koombiyo
     */
    private String pickupStatus;

    /**
     * Whether pickup request was successful
     */
    private Boolean success;

    /**
     * Message from Koombiyo API response
     */
    private String courierApiMessage;

    /**
     * HTTP status code from Koombiyo API
     */
    private Integer httpStatus;

    /**
     * Full API response body for debugging (logged only)
     */
    private String apiResponseBody;

    /**
     * Timestamp when pickup was requested
     */
    private LocalDateTime requestedAt;

    /**
     * Error message if request failed
     */
    private String errorMessage;
}

