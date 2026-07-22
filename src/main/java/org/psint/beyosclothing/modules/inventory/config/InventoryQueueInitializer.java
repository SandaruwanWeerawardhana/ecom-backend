package org.psint.beyosclothing.modules.inventory.config;

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
 * Inventory Module Queue Initializer
 * Ensures all inventory-related queues and bindings are properly declared
 * in RabbitMQ on every startup, including:
 * - Stock check requests (from products, POS, resellers)
 * - Stock reserve requests (from orders, resellers)
 * - Stock update events
 * - Low stock alerts
 */
@Component
@Slf4j
public class InventoryQueueInitializer {

    private final RabbitAdmin rabbitAdmin;
    private final TopicExchange inventoryExchange;

    @Value("${app.rabbitmq.queue.stock-check-request:inventory.stock.check}")
    private String stockCheckRequestQueueName;

    @Value("${app.rabbitmq.queue.stock-reserve-request:inventory.stock.reserve.request}")
    private String stockReserveRequestQueueName;

    @Value("${app.rabbitmq.queue.stock-updated:inventory.stock.updated}")
    private String stockUpdatedQueueName;

    @Value("${app.rabbitmq.queue.stock-status-update:inventory.stock.status.update.queue}")
    private String stockStatusUpdateQueueName;

    @Value("${app.rabbitmq.queue.low-stock-alert:inventory.low.stock.alert}")
    private String lowStockAlertQueueName;

    @Value("${app.rabbitmq.queue.stock-decrease-request:inventory.stock.decrease.request}")
    private String stockDecreaseRequestQueueName;

    public InventoryQueueInitializer(
            RabbitAdmin rabbitAdmin,
            @Qualifier("inventoryExchange") TopicExchange inventoryExchange) {
        this.rabbitAdmin = rabbitAdmin;
        this.inventoryExchange = inventoryExchange;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeQueuesAndBindings() {
        log.info("=== Initializing Inventory Module Queues and Bindings ===");

        try {
            // Ensure exchange exists
            rabbitAdmin.declareExchange(inventoryExchange);
            log.info("✅ Exchange declared: {}", inventoryExchange.getName());

            // ── Stock Check Request Queue (RPC from products, POS, resellers) ──
            Queue stockCheckQueue = QueueBuilder.durable(stockCheckRequestQueueName).build();
            rabbitAdmin.declareQueue(stockCheckQueue);
            Binding stockCheckBinding = BindingBuilder
                    .bind(stockCheckQueue)
                    .to(inventoryExchange)
                    .with("inventory.stock.check");
            rabbitAdmin.declareBinding(stockCheckBinding);
            log.info("✅ Binding declared: {} → inventory.stock.check", inventoryExchange.getName());

            // ── Stock Reserve Request Queue (RPC from order & reseller orders) ──
            Queue stockReserveQueue = QueueBuilder.durable(stockReserveRequestQueueName).build();
            rabbitAdmin.declareQueue(stockReserveQueue);
            Binding stockReserveBinding = BindingBuilder
                    .bind(stockReserveQueue)
                    .to(inventoryExchange)
                    .with("inventory.stock.reserve.request");
            rabbitAdmin.declareBinding(stockReserveBinding);
            log.info("✅ Binding declared: {} → inventory.stock.reserve.request", inventoryExchange.getName());

            // ── Stock Updated Queue (event from inventory module) ──
            Queue stockUpdatedQueue = QueueBuilder.durable(stockUpdatedQueueName).build();
            rabbitAdmin.declareQueue(stockUpdatedQueue);
            Binding stockUpdatedBinding = BindingBuilder
                    .bind(stockUpdatedQueue)
                    .to(inventoryExchange)
                    .with("inventory.stock.updated");
            rabbitAdmin.declareBinding(stockUpdatedBinding);
            log.info("✅ Binding declared: {} → inventory.stock.updated", inventoryExchange.getName());

            // ── Stock Status Update Queue (status changes) ──
            Queue stockStatusUpdateQueue = QueueBuilder.durable(stockStatusUpdateQueueName).build();
            rabbitAdmin.declareQueue(stockStatusUpdateQueue);
            Binding stockStatusUpdateBinding = BindingBuilder
                    .bind(stockStatusUpdateQueue)
                    .to(inventoryExchange)
                    .with("inventory.stock.status.update");
            rabbitAdmin.declareBinding(stockStatusUpdateBinding);
            log.info("✅ Binding declared: {} → inventory.stock.status.update", inventoryExchange.getName());

            // ── Low Stock Alert Queue (alerts for low stock) ──
            Queue lowStockAlertQueue = QueueBuilder.durable(lowStockAlertQueueName).build();
            rabbitAdmin.declareQueue(lowStockAlertQueue);
            Binding lowStockAlertBinding = BindingBuilder
                    .bind(lowStockAlertQueue)
                    .to(inventoryExchange)
                    .with("inventory.low.stock.alert");
            rabbitAdmin.declareBinding(lowStockAlertBinding);
            log.info("✅ Binding declared: {} → inventory.low.stock.alert", inventoryExchange.getName());

            // ── Stock Decrease Request Queue (RPC from order & POS modules) ──
            Queue stockDecreaseQueue = QueueBuilder.durable(stockDecreaseRequestQueueName).build();
            rabbitAdmin.declareQueue(stockDecreaseQueue);
            Binding stockDecreaseBinding = BindingBuilder
                    .bind(stockDecreaseQueue)
                    .to(inventoryExchange)
                    .with("inventory.stock.decrease.request");
            rabbitAdmin.declareBinding(stockDecreaseBinding);
            log.info("✅ Binding declared: {} → inventory.stock.decrease.request", inventoryExchange.getName());

            log.info("=== Inventory Module Queue Initialization Complete ===");

        } catch (Exception e) {
            log.error("❌ Failed to initialize Inventory module queues and bindings", e);
        }
    }
}

