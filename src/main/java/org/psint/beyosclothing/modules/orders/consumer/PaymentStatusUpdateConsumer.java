package org.psint.beyosclothing.modules.orders.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.orders.entity.OrderEntity;
import org.psint.beyosclothing.modules.orders.entity.OrderStatusHistoryEntity;
import org.psint.beyosclothing.modules.orders.repository.OrderRepository;
import org.psint.beyosclothing.modules.orders.repository.OrderStatusHistoryRepository;
import org.psint.beyosclothing.modules.orders.service.OrderFinalizationService;
import org.psint.beyosclothing.modules.sms.service.OrderSmsNotificationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * Consumer for payment status updates from the payment module (e.g. OnePay callback result).
 * Updates the order's payment status and, on a successful payment, its overall status.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentStatusUpdateConsumer {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final OrderSmsNotificationService smsNotificationService;
    private final OrderFinalizationService orderFinalizationService;

    /**
     * Event format: {
     *   orderId: Long,
     *   paymentStatus: String, // PAID or FAILED
     *   gatewayTransactionId: String
     * }
     */
    @RabbitListener(
            queues = "${app.rabbitmq.queue.payment-status-updated-order:payment.status.updated.order}",
            containerFactory = "orderRabbitListenerContainerFactory"
    )
    @Transactional("orderTransactionManager")
    public void handlePaymentStatusUpdate(Map<String, Object> event) {
        try {
            log.info("Received payment status update event: {}", event);

            Long orderId = null;
            Object orderIdObj = event.get("orderId");
            if (orderIdObj instanceof Number) {
                orderId = ((Number) orderIdObj).longValue();
            }

            if (orderId == null) {
                log.warn("Missing orderId in payment status update event");
                return;
            }

            String paymentStatus = (String) event.get("paymentStatus");
            String gatewayTransactionId = (String) event.get("gatewayTransactionId");

            Optional<OrderEntity> orderOpt = orderRepository.findById(orderId);
            if (orderOpt.isEmpty()) {
                log.warn("Order not found with ID: {}", orderId);
                return;
            }

            OrderEntity order = orderOpt.get();
            boolean paid = "PAID".equalsIgnoreCase(paymentStatus);

            OrderEntity.PaymentStatus newPaymentStatus = paid
                    ? OrderEntity.PaymentStatus.PAID
                    : OrderEntity.PaymentStatus.FAILED;

            if (order.getPaymentStatus() == newPaymentStatus) {
                log.debug("Order {} already has payment status: {}, skipping", orderId, newPaymentStatus);
                return;
            }

            String oldStatus = order.getStatus() != null ? order.getStatus().name() : null;
            // A successful payment on a still-PENDING order triggers the deferred finalization
            // (stock, cart, confirmation SMS) applied below. Only payment_status is updated here
            // (UNPAID -> PAID); the order status column is intentionally left unchanged.
            boolean confirmedByPayment = paid && order.getStatus() == OrderEntity.OrderStatus.PENDING;

            order.setPaymentStatus(newPaymentStatus);
            order.setPaymentReference(gatewayTransactionId);
            orderRepository.save(order);

            OrderStatusHistoryEntity historyEntry = OrderStatusHistoryEntity.builder()
                    .orderId(orderId)
                    .oldStatus(oldStatus)
                    .newStatus(order.getStatus() != null ? order.getStatus().name() : oldStatus)
                    .changedBy(null) // System-initiated change
                    .notes("Payment " + newPaymentStatus + " - Gateway Transaction: " + gatewayTransactionId)
                    .build();
            orderStatusHistoryRepository.save(historyEntry);

            log.info("Updated order {} payment status to {}", orderId, newPaymentStatus);

            if (confirmedByPayment) {
                // Online payment succeeded: apply the side effects that were deferred at placement
                // (decrease inventory, clear the cart) and send the held-back confirmation SMS.
                orderFinalizationService.finalizePaidOrder(order);
                smsNotificationService.sendOrderConfirmationForOrder(
                        order.getUuid(), order.getTotal(), order.getStatus().name());
            } else {
                smsNotificationService.sendOrderStatusUpdateForOrder(order.getUuid(), order.getStatus().name());
            }

        } catch (Exception e) {
            log.error("Error handling payment status update: {}", e.getMessage(), e);
        }
    }
}
