package org.psint.beyosclothing.modules.sms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Receives delivery-status push notifications from eSMS.
 *
 * eSMS calls this endpoint from its servers — the URL is registered per-send
 * via the {@code push_notification_url} field. It must be publicly accessible
 * (no auth token required).
 *
 * Status codes per eSMS API docs:
 *   1 = submitted to carrier
 *   2 = submission to carrier failed
 *   3 = delivered to handset
 *   4 = delivery to handset failed
 *
 * Note: statuses 1 and 3 may arrive out of order; do not assume sequence.
 */
@RestController
@RequestMapping("/api/sms")
@Slf4j
@Tag(name = "SMS Delivery Report", description = "eSMS push-notification callback — no authentication required")
public class SmsDeliveryReportController {

    private static final Map<Integer, String> STATUS_LABELS = Map.of(
            1, "SUBMITTED",
            2, "SUBMISSION_FAILED",
            3, "DELIVERED",
            4, "DELIVERY_FAILED"
    );

    @GetMapping("/delivery-report")
    @Operation(
            summary = "eSMS delivery report callback",
            description = "Called by eSMS servers with real-time delivery status for each recipient. "
                    + "Status: 1=submitted, 2=submission failed, 3=delivered, 4=delivery failed."
    )
    public ResponseEntity<Void> handleDeliveryReport(
            @RequestParam("campaignId") Long campaignId,
            @RequestParam("msisdn") String msisdn,
            @RequestParam("status") int status
    ) {
        String label = STATUS_LABELS.getOrDefault(status, "UNKNOWN_" + status);
        log.info("eSMS delivery report — campaignId: {}, msisdn: {}, status: {} ({})",
                campaignId, msisdn, status, label);
        return ResponseEntity.ok().build();
    }
}
