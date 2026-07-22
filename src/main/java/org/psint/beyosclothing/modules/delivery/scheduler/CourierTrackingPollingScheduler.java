package org.psint.beyosclothing.modules.delivery.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.delivery.service.CourierTrackingPollingService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler for polling courier tracking updates.
 * Runs every 10 minutes to fetch the latest shipment status from the Koombiyo API.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CourierTrackingPollingScheduler {

    private final CourierTrackingPollingService courierTrackingPollingService;

    /**
     * Poll courier tracking every 10 minutes.
     * Cron expression: 0 (asterisk)/10 * * * ? means every 10 minutes
     */
    @Scheduled(cron = "0 */10 * * * ?")
    public void pollCourierTracking() {
        log.info("⏱️  Triggering scheduled courier tracking polling task");

        try {
            int shipmentsPolled = courierTrackingPollingService.pollActiveShipmentsTracking();
            log.info("✅ Scheduled courier tracking polling completed. Shipments polled: {}", shipmentsPolled);
        } catch (Exception e) {
            log.error("❌ Scheduled courier tracking polling failed: {}", e.getMessage(), e);
        }
    }
}

