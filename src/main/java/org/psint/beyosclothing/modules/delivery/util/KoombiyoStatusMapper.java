package org.psint.beyosclothing.modules.delivery.util;

import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.delivery.entity.Shipment;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class to map Koombiyo API status strings to ShipmentStatus enum values
 *
 * This mapper handles all 33 Koombiyo courier statuses and maps them to the appropriate
 * ShipmentStatus enum for tracking shipments in the system.
 */
@Slf4j
public class KoombiyoStatusMapper {

    private static final Map<String, Shipment.ShipmentStatus> STATUS_MAP = new HashMap<>();

    static {
        // Koombiyo status string -> ShipmentStatus enum mapping (all 33 statuses)
        STATUS_MAP.put("Processing", Shipment.ShipmentStatus.PROCESSING);
        STATUS_MAP.put("Collected by Koombiyo", Shipment.ShipmentStatus.COLLECTED_BY_KOOMBIYO);
        STATUS_MAP.put("Dispatch to Destination", Shipment.ShipmentStatus.DISPATCH_TO_DESTINATION);
        STATUS_MAP.put("Received at Destination", Shipment.ShipmentStatus.RECEIVED_AT_DESTINATION);
        STATUS_MAP.put("Out for Delivery", Shipment.ShipmentStatus.OUT_FOR_DELIVERY);
        STATUS_MAP.put("Delivered", Shipment.ShipmentStatus.DELIVERED);
        STATUS_MAP.put("Rescheduled", Shipment.ShipmentStatus.RESCHEDULED);
        STATUS_MAP.put("Partially Delivered", Shipment.ShipmentStatus.PARTIALLY_DELIVERED);
        STATUS_MAP.put("Return to Client", Shipment.ShipmentStatus.RETURN_TO_CLIENT);
        STATUS_MAP.put("Failed to Deliver", Shipment.ShipmentStatus.FAILED_TO_DELIVER);
        STATUS_MAP.put("Return to HO", Shipment.ShipmentStatus.RETURN_TO_HO);
        STATUS_MAP.put("Different Destination", Shipment.ShipmentStatus.DIFFERENT_DESTINATION);
        STATUS_MAP.put("Received at HO", Shipment.ShipmentStatus.RECEIVED_AT_HO);
        STATUS_MAP.put("Received at Warehouse", Shipment.ShipmentStatus.RECEIVED_AT_WAREHOUSE);
        STATUS_MAP.put("Client Received", Shipment.ShipmentStatus.CLIENT_RECEIVED);
        STATUS_MAP.put("Purchase by Koombiyo", Shipment.ShipmentStatus.PURCHASE_BY_KOOMBIYO);
        STATUS_MAP.put("Delivered Not Confirmed", Shipment.ShipmentStatus.DELIVERED_NOT_CONFIRMED);
        STATUS_MAP.put("Partially Delivered Not Confirmed", Shipment.ShipmentStatus.PARTIALLY_DELIVERED_NOT_CONFIRMED);
        STATUS_MAP.put("Confirmed By Branch", Shipment.ShipmentStatus.CONFIRMED_BY_BRANCH);
        STATUS_MAP.put("Hold", Shipment.ShipmentStatus.HOLD);
        STATUS_MAP.put("Pending Different Destination", Shipment.ShipmentStatus.PENDING_DIFFERENT_DESTINATION);
        STATUS_MAP.put("On QC", Shipment.ShipmentStatus.ON_QC);
        STATUS_MAP.put("Picked", Shipment.ShipmentStatus.PICKED);
        STATUS_MAP.put("Exchange Collected", Shipment.ShipmentStatus.EXCHANGE_COLLECTED);
        STATUS_MAP.put("Exchange Received", Shipment.ShipmentStatus.EXCHANGE_RECEIVED);
    }

    /**
     * Map Koombiyo status string to ShipmentStatus enum
     *
     * @param koombiyoStatus Status string from Koombiyo API
     * @return Corresponding ShipmentStatus enum, or null if not found
     */
    public static Shipment.ShipmentStatus mapKoombiyoStatus(String koombiyoStatus) {
        if (koombiyoStatus == null || koombiyoStatus.isBlank()) {
            log.warn("Received null or empty Koombiyo status");
            return null;
        }

        String trimmedStatus = koombiyoStatus.trim();
        Shipment.ShipmentStatus mappedStatus = STATUS_MAP.get(trimmedStatus);

        if (mappedStatus == null) {
            log.warn("Unknown Koombiyo status received: '{}'. Available statuses: {}",
                    trimmedStatus, STATUS_MAP.keySet());
            return null;
        }

        log.debug("Mapped Koombiyo status '{}' to ShipmentStatus.{}", trimmedStatus, mappedStatus);
        return mappedStatus;
    }

    /**
     * Get all supported Koombiyo statuses
     *
     * @return Map of all Koombiyo statuses and their enum mappings
     */
    public static Map<String, Shipment.ShipmentStatus> getAllMappings() {
        return new HashMap<>(STATUS_MAP);
    }

    /**
     * Check if a Koombiyo status is supported
     *
     * @param koombiyoStatus Status string from Koombiyo API
     * @return true if the status is supported, false otherwise
     */
    public static boolean isSupported(String koombiyoStatus) {
        return koombiyoStatus != null && STATUS_MAP.containsKey(koombiyoStatus.trim());
    }

    /**
     * Check if shipment is in final/completed state
     *
     * @param status ShipmentStatus to check
     * @return true if status indicates shipment is completed
     */
    public static boolean isDelivered(Shipment.ShipmentStatus status) {
        return status == Shipment.ShipmentStatus.DELIVERED ||
               status == Shipment.ShipmentStatus.DELIVERED_NOT_CONFIRMED ||
               status == Shipment.ShipmentStatus.CLIENT_RECEIVED;
    }

    /**
     * Check if shipment is in failed/returned state
     *
     * @param status ShipmentStatus to check
     * @return true if status indicates shipment failed or returned
     */
    public static boolean isFinalFailed(Shipment.ShipmentStatus status) {
        return status == Shipment.ShipmentStatus.FAILED ||
               status == Shipment.ShipmentStatus.FAILED_TO_DELIVER ||
               status == Shipment.ShipmentStatus.RETURNED ||
               status == Shipment.ShipmentStatus.RETURN_TO_CLIENT ||
               status == Shipment.ShipmentStatus.RETURN_TO_HO;
    }

    /**
     * Check if shipment is in transit/processing
     *
     * @param status ShipmentStatus to check
     * @return true if status indicates shipment is in transit
     */
    public static boolean isInTransit(Shipment.ShipmentStatus status) {
        return status == Shipment.ShipmentStatus.BOOKED ||
               status == Shipment.ShipmentStatus.PROCESSING ||
               status == Shipment.ShipmentStatus.PICKED ||
               status == Shipment.ShipmentStatus.ON_QC ||
               status == Shipment.ShipmentStatus.IN_TRANSIT ||
               status == Shipment.ShipmentStatus.COLLECTED_BY_KOOMBIYO ||
               status == Shipment.ShipmentStatus.DISPATCH_TO_DESTINATION ||
               status == Shipment.ShipmentStatus.RECEIVED_AT_DESTINATION ||
               status == Shipment.ShipmentStatus.OUT_FOR_DELIVERY ||
               status == Shipment.ShipmentStatus.RESCHEDULED;
    }
}
