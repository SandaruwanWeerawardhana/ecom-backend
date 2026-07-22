package org.psint.beyosclothing.modules.resellers.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Reseller Queue Initializer
 * Ensures all reseller-related lookup queues and bindings are properly declared
 * in RabbitMQ on every startup. Runs AFTER the application is fully started
 * via ApplicationReadyEvent.
 * Initializes queues for:
 * - Reseller details/cart/order lookups
 * - Inventory stock check & reserve requests
 * - Delivery courier lookups
 * - Order creation requests
 * Solves the problem where RabbitMQ broker retains queues across restarts
 * but loses bindings — causing messages to be silently dropped.
 */
@Component
@Slf4j
public class ResellerQueueInitializer {

    private final RabbitAdmin rabbitAdmin;
    private final TopicExchange resellerExchange;
    private final TopicExchange inventoryExchange;
    private final TopicExchange deliveryExchange;
    private final TopicExchange orderExchange;

    @Value("${app.rabbitmq.queue.reseller-details-lookup-request:reseller.details.lookup.request}")
    private String resellerDetailsLookupQueueName;

    @Value("${app.rabbitmq.queue.reseller-name-lookup-request:reseller.name.lookup.request}")
    private String resellerNameLookupQueueName;

    @Value("${app.rabbitmq.queue.reseller-cart-items-lookup-request:reseller.cart.items.lookup.request}")
    private String resellerCartItemsLookupQueueName;

    @Value("${app.rabbitmq.queue.reseller-orders-list-lookup-request:reseller.orders.list.lookup.request}")
    private String resellerOrdersListLookupQueueName;

    @Value("${app.rabbitmq.queue.reseller-order-delivered:reseller.order.delivered.cart.lookup}")
    private String resellerOrderDeliveredQueueName;

    @Value("${app.rabbitmq.queue.stock-check-request:inventory.stock.check}")
    private String stockCheckRequestQueueName;

    @Value("${app.rabbitmq.queue.stock-reserve-request:inventory.stock.reserve.request}")
    private String stockReserveRequestQueueName;

    @Value("${app.rabbitmq.queue.stock-decrease-request:inventory.stock.decrease.request}")
    private String stockDecreaseRequestQueueName;

    @Value("${app.rabbitmq.queue.reseller-courier-lookup-request:reseller.courier.lookup.request.queue}")
    private String resellerCourierLookupRequestQueueName;

    @Value("${app.rabbitmq.queue.order-create-request:order.create.request}")
    private String orderCreateRequestQueueName;

    public ResellerQueueInitializer(
            RabbitAdmin rabbitAdmin,
            @Qualifier("resellerExchange") TopicExchange resellerExchange,
            @Qualifier("inventoryExchange") TopicExchange inventoryExchange,
            @Qualifier("deliveryExchange") TopicExchange deliveryExchange,
            @Qualifier("orderExchange") TopicExchange orderExchange) {
        this.rabbitAdmin = rabbitAdmin;
        this.resellerExchange = resellerExchange;
        this.inventoryExchange = inventoryExchange;
        this.deliveryExchange = deliveryExchange;
        this.orderExchange = orderExchange;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeQueuesAndBindings() {
        log.info("=== Initializing Reseller Module Queues and Bindings ===");

        try {
            // Ensure exchanges exist
            rabbitAdmin.declareExchange(resellerExchange);
            log.info("✅ Exchange declared: {}", resellerExchange.getName());

            rabbitAdmin.declareExchange(inventoryExchange);
            log.info("✅ Exchange declared: {}", inventoryExchange.getName());

            rabbitAdmin.declareExchange(deliveryExchange);
            log.info("✅ Exchange declared: {}", deliveryExchange.getName());

            rabbitAdmin.declareExchange(orderExchange);
            log.info("✅ Exchange declared: {}", orderExchange.getName());

            // ── RESELLER EXCHANGE QUEUES ──────────────────────────────────────

            // Reseller details lookup queue
            Queue resellerDetailsQueue = QueueBuilder.durable(resellerDetailsLookupQueueName).build();
            rabbitAdmin.declareQueue(resellerDetailsQueue);
            Binding resellerDetailsBinding = BindingBuilder.bind(resellerDetailsQueue)
                    .to(resellerExchange).with("reseller.details.lookup.request");
            rabbitAdmin.declareBinding(resellerDetailsBinding);
            log.info("✅ Binding declared: {} → reseller.details.lookup.request", resellerExchange.getName());

            // Reseller name lookup queue (for admin order list mapping)
            Queue resellerNameQueue = QueueBuilder.durable(resellerNameLookupQueueName).build();
            rabbitAdmin.declareQueue(resellerNameQueue);
            Binding resellerNameBinding = BindingBuilder.bind(resellerNameQueue)
                    .to(resellerExchange).with("reseller.name.lookup.request");
            rabbitAdmin.declareBinding(resellerNameBinding);
            log.info("✅ Binding declared: {} → reseller.name.lookup.request", resellerExchange.getName());

            // Reseller cart items lookup queue
            Queue resellerCartItemsQueue = QueueBuilder.durable(resellerCartItemsLookupQueueName).build();
            rabbitAdmin.declareQueue(resellerCartItemsQueue);
            Binding resellerCartItemsBinding = BindingBuilder.bind(resellerCartItemsQueue)
                    .to(resellerExchange).with("reseller.cart.items.lookup.request");
            rabbitAdmin.declareBinding(resellerCartItemsBinding);
            log.info("✅ Binding declared: {} → reseller.cart.items.lookup.request", resellerExchange.getName());

            // Reseller orders list lookup queue
            Queue resellerOrdersListQueue = QueueBuilder.durable(resellerOrdersListLookupQueueName).build();
            rabbitAdmin.declareQueue(resellerOrdersListQueue);
            Binding resellerOrdersListBinding = BindingBuilder.bind(resellerOrdersListQueue)
                    .to(resellerExchange).with("reseller.orders.list.lookup.request");
            rabbitAdmin.declareBinding(resellerOrdersListBinding);
            log.info("✅ Binding declared: {} → reseller.orders.list.lookup.request", resellerExchange.getName());

            // Reseller order delivered wallet credit queue
            Queue resellerOrderDeliveredQueue = QueueBuilder.durable(resellerOrderDeliveredQueueName).build();
            rabbitAdmin.declareQueue(resellerOrderDeliveredQueue);
            Binding resellerOrderDeliveredBinding = BindingBuilder.bind(resellerOrderDeliveredQueue)
                    .to(resellerExchange).with("reseller.order.delivered.cart.lookup");
            rabbitAdmin.declareBinding(resellerOrderDeliveredBinding);
            log.info("✅ Binding declared: {} → reseller.order.delivered.cart.lookup", resellerExchange.getName());

            // Reseller Password update
            Queue resellerPasswordUpdateQueue = QueueBuilder.durable("admin.user.password.update").build();
            rabbitAdmin.declareQueue(resellerPasswordUpdateQueue);
            Binding resellerPasswordUpdateBinding = BindingBuilder.bind(resellerPasswordUpdateQueue)
                    .to(resellerExchange).with("admin.user.password.update");
            rabbitAdmin.declareBinding(resellerPasswordUpdateBinding);
            log.info("✅ Binding declared: {} → admin.user.password.update", resellerExchange.getName());

            // ── INVENTORY EXCHANGE QUEUES (for reseller order operations) ────────────────

            // Stock check request queue (RPC from reseller order service)
            Queue stockCheckQueue = QueueBuilder.durable(stockCheckRequestQueueName).build();
            rabbitAdmin.declareQueue(stockCheckQueue);
            Binding stockCheckBinding = BindingBuilder.bind(stockCheckQueue)
                    .to(inventoryExchange).with("inventory.stock.check");
            rabbitAdmin.declareBinding(stockCheckBinding);
            log.info("✅ Binding declared: {} → inventory.stock.check", inventoryExchange.getName());

            // Stock reserve request queue (RPC from reseller order service)
            Queue stockReserveQueue = QueueBuilder.durable(stockReserveRequestQueueName).build();
            rabbitAdmin.declareQueue(stockReserveQueue);
            Binding stockReserveBinding = BindingBuilder.bind(stockReserveQueue)
                    .to(inventoryExchange).with("inventory.stock.reserve.request");
            rabbitAdmin.declareBinding(stockReserveBinding);
            log.info("✅ Binding declared: {} → inventory.stock.reserve.request", inventoryExchange.getName());

            // Stock decrease request queue (RPC from reseller order service) — decrements
            Queue stockDecreaseQueue = QueueBuilder.durable(stockDecreaseRequestQueueName).build();
            rabbitAdmin.declareQueue(stockDecreaseQueue);
            Binding stockDecreaseBinding = BindingBuilder.bind(stockDecreaseQueue)
                    .to(inventoryExchange).with("inventory.stock.decrease.request");
            rabbitAdmin.declareBinding(stockDecreaseBinding);
            log.info("✅ Binding declared: {} → inventory.stock.decrease.request", inventoryExchange.getName());

            // ── DELIVERY EXCHANGE QUEUES (for reseller courier lookups) ─────────────────

            // Reseller courier lookup request (RPC from reseller order service)
            Queue resellerCourierQueue = QueueBuilder.durable(resellerCourierLookupRequestQueueName).build();
            rabbitAdmin.declareQueue(resellerCourierQueue);
            Binding resellerCourierBinding = BindingBuilder.bind(resellerCourierQueue)
                    .to(deliveryExchange).with("reseller.courier.lookup.request");
            rabbitAdmin.declareBinding(resellerCourierBinding);
            log.info("✅ Binding declared: {} → reseller.courier.lookup.request", deliveryExchange.getName());

            // ── ORDER EXCHANGE QUEUES (for reseller order creation) ────────────────────

            // Order create request (RPC from reseller order service)
            Queue orderCreateQueue = QueueBuilder.durable(orderCreateRequestQueueName).build();
            rabbitAdmin.declareQueue(orderCreateQueue);
            Binding orderCreateBinding = BindingBuilder.bind(orderCreateQueue)
                    .to(orderExchange).with("order.create.request");
            rabbitAdmin.declareBinding(orderCreateBinding);
            log.info("✅ Binding declared: {} → order.create.request", orderExchange.getName());

            log.info("=== Reseller Module Queue Initialization Complete ===");
        } catch (Exception e) {
            log.error("❌ Failed to initialize Reseller module queues and bindings", e);
        }
    }
}
