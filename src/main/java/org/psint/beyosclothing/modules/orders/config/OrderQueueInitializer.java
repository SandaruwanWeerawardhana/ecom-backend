package org.psint.beyosclothing.modules.orders.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Ensures critical order module bindings are created
 * Runs after application is fully initialized
 */
@Component
@Slf4j
public class OrderQueueInitializer {

    private final RabbitAdmin rabbitAdmin;
    private final Queue orderCreateRequestQueue;
    private final Queue orderDetailLookupRequestQueue;
    private final Queue resellerOrdersListLookupRequestQueue;
    private final Queue resellerPendingOrdersListLookupRequestQueue;
    private final TopicExchange orderExchange;

    @Value("${app.rabbitmq.exchange.order:beyos.exchange.order}")
    private String orderExchangeName;

    @Value("${app.rabbitmq.queue.order-status-updated-courier:order.status.updated.courier}")
    private String orderStatusUpdatedCourierQueueName;

    @Value("${app.rabbitmq.queue.order-delivery-completed:order.delivery.completed}")
    private String orderDeliveryCompletedQueueName;

    @Value("${app.rabbitmq.queue.shipment-pickup-requested:shipment.pickup.requested}")
    private String shipmentPickupRequestedQueueName;

    public OrderQueueInitializer(
            RabbitAdmin rabbitAdmin,
            @Qualifier("orderCreateRequestQueue") Queue orderCreateRequestQueue,
            @Qualifier("orderDetailLookupRequestQueue") Queue orderDetailLookupRequestQueue,
            @Qualifier("resellerOrdersListLookupRequestQueue") Queue resellerOrdersListLookupRequestQueue,
            @Qualifier("resellerPendingOrdersListLookupRequestQueue") Queue resellerPendingOrdersListLookupRequestQueue,
            @Qualifier("orderExchange") TopicExchange orderExchange) {
        this.rabbitAdmin = rabbitAdmin;
        this.orderCreateRequestQueue = orderCreateRequestQueue;
        this.orderDetailLookupRequestQueue = orderDetailLookupRequestQueue;
        this.resellerOrdersListLookupRequestQueue = resellerOrdersListLookupRequestQueue;
        this.resellerPendingOrdersListLookupRequestQueue = resellerPendingOrdersListLookupRequestQueue;
        this.orderExchange = orderExchange;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeQueuesAndBindings() {
        log.info("=== Initializing Order Module Queues and Bindings ===");

        try {
            // Ensure exchange exists
            rabbitAdmin.declareExchange(orderExchange);
            log.info("✅ Exchange declared: {}", orderExchange.getName());

            // ========================================
            // Order Creation Request
            // ========================================
            rabbitAdmin.declareQueue(orderCreateRequestQueue);
            log.info("✅ Queue declared: {}", orderCreateRequestQueue.getName());

            Binding orderCreateBinding = BindingBuilder
                    .bind(orderCreateRequestQueue)
                    .to(orderExchange)
                    .with("order.create.request");

            rabbitAdmin.declareBinding(orderCreateBinding);
            log.info("✅ Binding created: {} -> {} with routing key: order.create.request",
                    orderExchange.getName(), orderCreateRequestQueue.getName());

            // ========================================
            // Order Detail Lookup Request
            // ========================================
            rabbitAdmin.declareQueue(orderDetailLookupRequestQueue);
            log.info("✅ Queue declared: {}", orderDetailLookupRequestQueue.getName());

            Binding orderDetailBinding = BindingBuilder
                    .bind(orderDetailLookupRequestQueue)
                    .to(orderExchange)
                    .with("order.detail.lookup.request");

            rabbitAdmin.declareBinding(orderDetailBinding);
            log.info("✅ Binding created: {} -> {} with routing key: order.detail.lookup.request",
                    orderExchange.getName(), orderDetailLookupRequestQueue.getName());

            // ========================================
            // Reseller All Orders List Lookup Request
            // ========================================
            rabbitAdmin.declareQueue(resellerOrdersListLookupRequestQueue);
            log.info("✅ Queue declared: {}", resellerOrdersListLookupRequestQueue.getName());

            Binding resellerOrdersBinding = BindingBuilder
                    .bind(resellerOrdersListLookupRequestQueue)
                    .to(orderExchange)
                    .with("reseller.orders.list.lookup.request");

            rabbitAdmin.declareBinding(resellerOrdersBinding);
            log.info("✅ Binding created: {} -> {} with routing key: reseller.orders.list.lookup.request",
                    orderExchange.getName(), resellerOrdersListLookupRequestQueue.getName());

            // ========================================
            // Reseller Pending Orders List Lookup Request
            // ========================================
            rabbitAdmin.declareQueue(resellerPendingOrdersListLookupRequestQueue);
            log.info("✅ Queue declared: {}", resellerPendingOrdersListLookupRequestQueue.getName());

            Binding resellerPendingOrdersBinding = BindingBuilder
                    .bind(resellerPendingOrdersListLookupRequestQueue)
                    .to(orderExchange)
                    .with("reseller.pending.orders.list.lookup.request");

            rabbitAdmin.declareBinding(resellerPendingOrdersBinding);
            log.info("✅ Binding created: {} -> {} with routing key: reseller.pending.orders.list.lookup.request",
                    orderExchange.getName(), resellerPendingOrdersListLookupRequestQueue.getName());

            // ========================================
            // Order Status Updated Courier
            // ========================================
            Queue orderStatusUpdatedCourierQueue = new Queue(orderStatusUpdatedCourierQueueName, true);
            rabbitAdmin.declareQueue(orderStatusUpdatedCourierQueue);
            log.info("✅ Queue declared: {}", orderStatusUpdatedCourierQueue.getName());

            Binding orderStatusUpdatedCourierBinding = BindingBuilder
                    .bind(orderStatusUpdatedCourierQueue)
                    .to(orderExchange)
                    .with("order.status.updated.courier");

            rabbitAdmin.declareBinding(orderStatusUpdatedCourierBinding);
            log.info("✅ Binding created: {} -> {} with routing key: order.status.updated.courier",
                    orderExchange.getName(), orderStatusUpdatedCourierQueue.getName());

            // ========================================
            // Order Delivery Completed
            // ========================================
            Queue orderDeliveryCompletedQueue = new Queue(orderDeliveryCompletedQueueName, true);
            rabbitAdmin.declareQueue(orderDeliveryCompletedQueue);
            log.info("✅ Queue declared: {}", orderDeliveryCompletedQueue.getName());

            Binding orderDeliveryCompletedBinding = BindingBuilder
                    .bind(orderDeliveryCompletedQueue)
                    .to(orderExchange)
                    .with("order.delivery.completed");

            rabbitAdmin.declareBinding(orderDeliveryCompletedBinding);
            log.info("✅ Binding created: {} -> {} with routing key: order.delivery.completed",
                    orderExchange.getName(), orderDeliveryCompletedQueue.getName());

            // ========================================
            // Shipment Pickup Requested
            // ========================================
//            Queue shipmentPickupRequestedQueue = new Queue(shipmentPickupRequestedQueueName, true);
//            rabbitAdmin.declareQueue(shipmentPickupRequestedQueue);
//            log.info("✅ Queue declared: {}", shipmentPickupRequestedQueue.getName());
//
//            Binding shipmentPickupRequestedBinding = BindingBuilder
//                    .bind(shipmentPickupRequestedQueue)
//                    .to(orderExchange)
//                    .with("shipment.pickup.requested");
//
//            rabbitAdmin.declareBinding(shipmentPickupRequestedBinding);
            Queue shipmentPickupRequestedQueue = new Queue(shipmentPickupRequestedQueueName, true);
            rabbitAdmin.declareQueue(shipmentPickupRequestedQueue);
            log.info("✅ Queue declared: {}", shipmentPickupRequestedQueue.getName());

            Binding shipmentPickupRequestedBinding = BindingBuilder
                    .bind(shipmentPickupRequestedQueue)
                    .to(orderExchange)
                    .with("shipment.pickup.requested");

            rabbitAdmin.declareBinding(shipmentPickupRequestedBinding);
            log.info("✅ Binding created: {} -> {} with routing key: shipment.pickup.requested",
                    orderExchange.getName(), shipmentPickupRequestedQueue.getName());

            log.info("=== Order Module Initialization Complete ===");

        } catch (Exception e) {
            log.error("❌ Failed to initialize order module queues and bindings", e);
        }
    }
}
