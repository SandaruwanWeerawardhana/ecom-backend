package org.psint.beyosclothing.modules.pos.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.pos.entity.PosStockSyncEntity;
import org.psint.beyosclothing.modules.pos.repository.PosStockSyncRepository;
import org.psint.beyosclothing.modules.pos.service.PosInventoryEventService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * POS Inventory Reconciliation Scheduler
 * Nightly job to detect and retry unsynced inventory deductions
 * Generates alerts for records requiring manual review
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PosInventoryReconciliationScheduler {

    private final PosInventoryEventService inventoryEventService;
    private final PosStockSyncRepository stockSyncRepository;

    @Scheduled(cron = "0 0 2 * * ?")
    public void reconcileInventoryDeductions() {
        log.info("Starting nightly inventory reconciliation job");

        try {
            int retriedCount = inventoryEventService.retryPendingDeductions();
            log.info("Retried {} pending inventory deductions", retriedCount);

            generateReconciliationReport();

            log.info("Inventory reconciliation job completed successfully");

        } catch (Exception e) {
            log.error("Inventory reconciliation job failed: {}", e.getMessage(), e);
        }
    }

    @Scheduled(cron = "0 */15 * * * ?")
    public void retryFailedDeductions() {
        log.debug("Running periodic retry of failed deductions");

        try {
            int retriedCount = inventoryEventService.retryPendingDeductions();

            if (retriedCount > 0) {
                log.info("Retried {} pending deductions in periodic job", retriedCount);
            }

        } catch (Exception e) {
            log.error("Periodic retry job failed: {}", e.getMessage());
        }
    }

    private void generateReconciliationReport() {
        LocalDateTime last24Hours = LocalDateTime.now().minusHours(24);

        long unsyncedCount = stockSyncRepository.countBySynced(false);
        long syncedCount = stockSyncRepository.countBySynced(true);

        log.info("=== Inventory Sync Status Report ===");
        log.info("Synced: {}", syncedCount);
        log.info("Unsynced (Pending): {}", unsyncedCount);

        if (unsyncedCount > 0) {
            log.warn("ALERT: {} inventory deductions are unsynced", unsyncedCount);

            List<PosStockSyncEntity> unsyncedRecords = stockSyncRepository.findUnsyncedOlderThan(last24Hours);

            if (!unsyncedRecords.isEmpty()) {
                log.error("ALERT: {} unsynced records older than 24 hours require review", unsyncedRecords.size());

                unsyncedRecords.forEach(record -> {
                    log.error("Order ID {} (UUID: {}) requires review - Product: {}, Variant: {}, Quantity: {}",
                            record.getPosOrderId(), record.getUuid(), record.getProductId(),
                            record.getVariantId(), record.getQuantity());
                });
            }
        }

        if (unsyncedCount > 10) {
            log.warn("ALERT: High number of pending inventory deductions: {}", unsyncedCount);
        }
    }
}
