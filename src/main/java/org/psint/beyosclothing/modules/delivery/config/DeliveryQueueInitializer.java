package org.psint.beyosclothing.modules.delivery.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Ensures critical delivery module bindings are created
 * Runs after application is fully initialized
 */
@Component
@Slf4j
public class DeliveryQueueInitializer {

    private final RabbitAdmin rabbitAdmin;
    private final Queue resellerCourierLookupQueue;
    private final TopicExchange deliveryExchange;

    @Value("${app.rabbitmq.exchange.delivery:beyos.exchange.delivery}")
    private String deliveryExchangeName;

    @Value("${app.rabbitmq.queue.shipment-lookup-request:shipment.lookup.request.queue}")
    private String shipmentLookupQueueName;

    @Value("${app.rabbitmq.queue.shipment-placed-with-courier:shipment.placed.with.courier}")
    private String shipmentPlacedWithCourierQueueName;

    @Value("${app.rabbitmq.queue.shipment-create-or-get-request:shipment.create.or.get.request}")
    private String shipmentCreateOrGetRequestQueueName;

    @Value("${app.rabbitmq.queue.courier-api-call-log:courier.api.call.log}")
    private String courierApiCallLogQueueName;

    @Value("${app.rabbitmq.queue.shipment-status-update:shipment.status.update}")
    private String shipmentStatusUpdateQueueName;

    @Value("${app.rabbitmq.queue.shipment-pickup-requested:shipment.pickup.requested}")
    private String shipmentPickupRequestedQueueName;

    @Value("${app.rabbitmq.queue.shipment-pickup-requested-delivery:shipment.pickup.requested.delivery}")
    private String shipmentPickupRequestedDeliveryQueueName;


    public DeliveryQueueInitializer(
            RabbitAdmin rabbitAdmin,
            @Qualifier("resellerCourierLookupRequestQueue") Queue resellerCourierLookupQueue,
            @Qualifier("deliveryExchange") TopicExchange deliveryExchange) {
        this.rabbitAdmin = rabbitAdmin;
        this.resellerCourierLookupQueue = resellerCourierLookupQueue;
        this.deliveryExchange = deliveryExchange;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeQueuesAndBindings() {
        log.info("=== Initializing Delivery Module Queues and Bindings ===");

        try {
            // Ensure queue exists
            rabbitAdmin.declareQueue(resellerCourierLookupQueue);
            log.info("✅ Queue declared: {}", resellerCourierLookupQueue.getName());

            // Ensure exchange exists
            rabbitAdmin.declareExchange(deliveryExchange);
            log.info("✅ Exchange declared: {}", deliveryExchange.getName());

            // Create binding
            Binding courierBinding = BindingBuilder
                    .bind(resellerCourierLookupQueue)
                    .to(deliveryExchange)
                    .with("reseller.courier.lookup.request");

            rabbitAdmin.declareBinding(courierBinding);
            log.info("✅ Binding created: {} -> {} with routing key: reseller.courier.lookup.request",
                    deliveryExchange.getName(), resellerCourierLookupQueue.getName());

            // Shipment lookup queue (NEW)
            Queue shipmentLookupQueue = QueueBuilder.durable(shipmentLookupQueueName).build();
            rabbitAdmin.declareQueue(shipmentLookupQueue);
            Binding shipmentBinding = BindingBuilder
                    .bind(shipmentLookupQueue)
                    .to(deliveryExchange)
                    .with("shipment.lookup.request");

            rabbitAdmin.declareBinding(shipmentBinding);
            log.info("✅ Binding created: {} → shipment.lookup.request", deliveryExchange.getName());

            // Shipment placed with courier queue (for order placement events)
            Queue shipmentPlacedWithCourierQueue = QueueBuilder.durable(shipmentPlacedWithCourierQueueName).build();
            rabbitAdmin.declareQueue(shipmentPlacedWithCourierQueue);
            Binding shipmentPlacedBinding = BindingBuilder
                    .bind(shipmentPlacedWithCourierQueue)
                    .to(deliveryExchange)
                    .with("shipment.placed.with.courier");

            rabbitAdmin.declareBinding(shipmentPlacedBinding);
            log.info("✅ Binding created: {} → shipment.placed.with.courier", deliveryExchange.getName());

            // Shipment create or get request queue (NEW)
            Queue shipmentCreateOrGetRequestQueue = QueueBuilder.durable(shipmentCreateOrGetRequestQueueName).build();
            rabbitAdmin.declareQueue(shipmentCreateOrGetRequestQueue);
            Binding shipmentCreateOrGetRequestBinding = BindingBuilder
                    .bind(shipmentCreateOrGetRequestQueue)
                    .to(deliveryExchange)
                    .with("shipment.create.or.get.request");

            rabbitAdmin.declareBinding(shipmentCreateOrGetRequestBinding);
            log.info("✅ Binding created: {} → shipment.create.or.get.request", deliveryExchange.getName());

            // Courier API call log queue (NEW)
            Queue courierApiCallLogQueue = QueueBuilder.durable(courierApiCallLogQueueName).build();
            rabbitAdmin.declareQueue(courierApiCallLogQueue);
            Binding courierApiCallLogBinding = BindingBuilder
                    .bind(courierApiCallLogQueue)
                    .to(deliveryExchange)
                    .with("courier.api.call.log");

            rabbitAdmin.declareBinding(courierApiCallLogBinding);
            log.info("✅ Binding created: {} → courier.api.call.log", deliveryExchange.getName());

            // Shipment status update queue (NEW)
            Queue shipmentStatusUpdateQueue = QueueBuilder.durable(shipmentStatusUpdateQueueName).build();
            rabbitAdmin.declareQueue(shipmentStatusUpdateQueue);
            Binding shipmentStatusUpdateBinding = BindingBuilder
                    .bind(shipmentStatusUpdateQueue)
                    .to(deliveryExchange)
                    .with("shipment.status.update");

            rabbitAdmin.declareBinding(shipmentStatusUpdateBinding);
            log.info("✅ Binding created: {} → shipment.status.update", deliveryExchange.getName());

            // Shipment pickup requested queue (NEW)
//            Queue shipmentPickupRequestedQueue = QueueBuilder.durable(shipmentPickupRequestedQueueName).build();
//            rabbitAdmin.declareQueue(shipmentPickupRequestedQueue);
//            Binding shipmentPickupRequestedBinding = BindingBuilder
//                    .bind(shipmentPickupRequestedQueue)
//                    .to(deliveryExchange)
//                    .with("shipment.pickup.requested");
            // Shipment pickup requested queue - DELIVERY MODULE (separate from order module)
            Queue shipmentPickupRequestedDeliveryQueue = QueueBuilder.durable(shipmentPickupRequestedDeliveryQueueName).build();
            rabbitAdmin.declareQueue(shipmentPickupRequestedDeliveryQueue);
            Binding shipmentPickupRequestedDeliveryBinding = BindingBuilder
                    .bind(shipmentPickupRequestedDeliveryQueue)
                    .to(deliveryExchange)
                    .with("shipment.pickup.requested");

            rabbitAdmin.declareBinding(shipmentPickupRequestedDeliveryBinding);
            log.info("✅ Binding created: {} → shipment.pickup.requested", deliveryExchange.getName());

            log.info("=== Delivery Module Initialization Complete ===");

        } catch (Exception e) {
            log.error("❌ Failed to initialize delivery module queues and bindings", e);
        }
    }
}
