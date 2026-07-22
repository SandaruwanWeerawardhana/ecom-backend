package org.psint.beyosclothing.modules.pos.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.orders.entity.OrderEntity;
import org.psint.beyosclothing.modules.orders.entity.OrderItemEntity;
import org.psint.beyosclothing.modules.orders.repository.OrderItemRepository;
import org.psint.beyosclothing.modules.orders.repository.OrderRepository;
import org.psint.beyosclothing.modules.pos.dto.event.PosOrderPlacedEvent;
import org.psint.beyosclothing.modules.pos.entity.PosStockSyncEntity;
import org.psint.beyosclothing.modules.pos.repository.PosStockSyncRepository;
import org.psint.beyosclothing.modules.pos.service.PosInventoryEventService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * POS Inventory Event Service Implementation
 * Asynchronous, event-driven inventory deduction workflow
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PosInventoryEventServiceImpl implements PosInventoryEventService {

    private final PosStockSyncRepository stockSyncRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderRepository orderRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ApplicationContext applicationContext;

    private static final String POS_INVENTORY_EXCHANGE = "pos.inventory.exchange";
    private static final String POS_INVENTORY_ROUTING_KEY = "pos.inventory.deduct";
    private static final int RETRY_DELAY_MINUTES = 15;

    @Override
    @Async
    @Transactional("posTransactionManager")
    public void publishInventoryDeductionEvent(OrderEntity order) {
        log.info("Publishing inventory deduction event for order: {}", order.getOrderNumber());

        try {
            List<OrderItemEntity> orderItems = orderItemRepository.findByOrderId(order.getId());

            if (orderItems.isEmpty()) {
                log.warn("No order items found for order: {}", order.getId());
                return;
            }

            createStockSyncAuditRecords(order, orderItems);

            PosOrderPlacedEvent event = buildInventoryDeductionEvent(order, orderItems);

            rabbitTemplate.convertAndSend(
                    POS_INVENTORY_EXCHANGE,
                    POS_INVENTORY_ROUTING_KEY,
                    event
            );

            log.info("Inventory deduction event published successfully - order: {}, items: {}",
                    order.getOrderNumber(), orderItems.size());

        } catch (Exception e) {
            log.error("Failed to publish inventory deduction event for order {}: {}",
                    order.getOrderNumber(), e.getMessage(), e);

            markAllItemsAsFailed(order.getId(), order.getUuid(), e.getMessage());
        }
    }

    private void createStockSyncAuditRecords(OrderEntity order, List<OrderItemEntity> orderItems) {
        log.debug("Creating stock sync audit records for {} items", orderItems.size());

        List<PosStockSyncEntity> syncRecords = orderItems.stream()
                .map(item -> PosStockSyncEntity.builder()
                        .posOrderId(order.getId())
                        .productId(item.getProductId())
                        .variantId(item.getVariantId())
                        .quantity(item.getQuantity())
                        .synced(false)
                        .build())
                .toList();

        stockSyncRepository.saveAll(syncRecords);

        log.info("Created {} stock sync audit records for order: {}",
                syncRecords.size(), order.getOrderNumber());
    }

    private PosOrderPlacedEvent buildInventoryDeductionEvent(OrderEntity order, List<OrderItemEntity> orderItems) {
        List<PosOrderPlacedEvent.OrderItemData> items = orderItems.stream()
                .map(item -> PosOrderPlacedEvent.OrderItemData.builder()
                        .orderItemId(item.getId())
                        .productId(item.getProductId())
                        .variantId(item.getVariantId())
                        .quantity(item.getQuantity())
                        .build())
                .toList();

        return PosOrderPlacedEvent.builder()
                .eventType("POS_ORDER_PLACED")
                .orderId(order.getId())
                .orderUuid(order.getUuid())
                .items(items)
                .timestamp(LocalDateTime.now())
                .build();
    }

    @Override
    @Transactional("posTransactionManager")
    public void handleDeductionConfirmation(Long orderId, String orderUuid, boolean success, String errorMessage) {
        log.info("Handling deduction confirmation - order: {}, success: {}", orderUuid, success);

        List<PosStockSyncEntity> syncRecords = stockSyncRepository.findByPosOrderId(orderId);

        if (syncRecords.isEmpty()) {
            log.warn("No sync records found for order: {}", orderId);
            return;
        }

        if (success) {
            syncRecords.forEach(rec -> {
                rec.setSynced(true);
                rec.setSyncedAt(LocalDateTime.now());
            });

            log.info("Marked {} items as SYNCED for order: {}", syncRecords.size(), orderUuid);

        } else {
            log.warn("Deduction failed for order: {}, items: {}, error: {}",
                    orderUuid, syncRecords.size(), errorMessage);

            // Call via proxy to ensure transactional behavior
            applicationContext.getBean(PosInventoryEventService.class).markOrderForManualReview(orderId, "Inventory deduction failed: " + errorMessage);
        }

        stockSyncRepository.saveAll(syncRecords);
    }

    @Override
    @Transactional("posTransactionManager")
    public int retryPendingDeductions() {
        log.info("Starting retry of pending inventory deductions");

        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(RETRY_DELAY_MINUTES);

        List<PosStockSyncEntity> pendingRecords = stockSyncRepository.findUnsyncedOlderThan(cutoffTime);

        if (pendingRecords.isEmpty()) {
            log.info("No pending records found for retry");
            return 0;
        }

        log.info("Found {} pending records for retry", pendingRecords.size());

        pendingRecords.stream()
                .collect(Collectors.groupingBy(PosStockSyncEntity::getPosOrderId))
                .forEach((orderId, records) -> {
                    try {
                        OrderEntity order = orderRepository.findById(orderId)
                                .orElseThrow(() -> new IllegalStateException("Order not found: " + orderId));

                        publishInventoryDeductionEvent(order);

                        log.info("Retried inventory deduction for order: {}, items: {}",
                                order.getOrderNumber(), records.size());

                    } catch (Exception e) {
                        log.error("Failed to retry order {}: {}", orderId, e.getMessage());
                        applicationContext.getBean(PosInventoryEventService.class).markOrderForManualReview(orderId, "Retry failed: " + e.getMessage());
                    }
                });

        return pendingRecords.size();
    }

    @Override
    @Transactional("orderTransactionManager")
    public void markOrderForManualReview(Long orderId, String reason) {
        log.warn("Marking order {} for manual review: {}", orderId, reason);

        try {
            OrderEntity order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new IllegalStateException("Order not found: " + orderId));

            String currentNotes = order.getNotes() != null ? order.getNotes() + " | " : "";
            order.setNotes(currentNotes + "REQUIRES MANUAL REVIEW: " + reason);

            orderRepository.save(order);

            log.error("ALERT: Order {} requires manual inventory review - Reason: {}",
                    order.getOrderNumber(), reason);

        } catch (Exception e) {
            log.error("Failed to mark order {} for review: {}", orderId, e.getMessage());
        }
    }

    private void markAllItemsAsFailed(Long orderId, String orderUuid, String errorMessage) {
        try {
            List<PosStockSyncEntity> syncRecords = stockSyncRepository.findByPosOrderId(orderId);

            syncRecords.forEach(rec -> {
                rec.setSynced(false);
            });

            stockSyncRepository.saveAll(syncRecords);

            log.warn("Marked {} items as FAILED for order: {}", syncRecords.size(), orderUuid);

            applicationContext.getBean(PosInventoryEventService.class).markOrderForManualReview(orderId, "Event publication failed: " + errorMessage);

        } catch (Exception e) {
            log.error("Failed to mark items as failed for order {}: {}", orderId, e.getMessage());
        }
    }
}
