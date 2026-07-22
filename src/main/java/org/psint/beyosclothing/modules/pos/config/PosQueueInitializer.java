package org.psint.beyosclothing.modules.pos.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Ensures critical POS module bindings are created on every startup.
 * Runs after application is fully initialized via ApplicationReadyEvent.
 *
 * Solves the problem where RabbitMQ broker retains queues across restarts
 * but loses bindings — causing messages to be silently dropped.
 */
@Component
@Slf4j
public class PosQueueInitializer {

    private final RabbitAdmin rabbitAdmin;
    private final Queue posVariantLookupRequestQueue;
    private final Queue posCustomerLookupRequestQueue;
    private final Queue posProductListRequestQueue;
    private final Queue posCashierLookupRequestQueue;
    private final Queue posCashierByAdminLookupRequestQueue;
    private final TopicExchange productExchange;
    private final TopicExchange customerExchange;
    private final TopicExchange posExchange;

    @Value("${app.rabbitmq.exchange.product:product.exchange}")
    private String productExchangeName;

    @Value("${app.rabbitmq.exchange.customer:beyos.exchange.customer}")
    private String customerExchangeName;

    @Value("${app.rabbitmq.exchange.pos:pos.exchange}")
    private String posExchangeName;

    public PosQueueInitializer(
            RabbitAdmin rabbitAdmin,
            @Qualifier("posVariantLookupRequestQueue") Queue posVariantLookupRequestQueue,
            @Qualifier("posCustomerLookupRequestQueue") Queue posCustomerLookupRequestQueue,
            @Qualifier("posProductListRequestQueue") Queue posProductListRequestQueue,
            @Qualifier("posCashierLookupRequestQueue") Queue posCashierLookupRequestQueue,
            @Qualifier("posCashierByAdminLookupRequestQueue") Queue posCashierByAdminLookupRequestQueue,
            @Qualifier("productExchange") TopicExchange productExchange,
            @Qualifier("customerExchange") TopicExchange customerExchange,
            @Qualifier("posExchange") TopicExchange posExchange) {
        this.rabbitAdmin = rabbitAdmin;
        this.posVariantLookupRequestQueue = posVariantLookupRequestQueue;
        this.posCustomerLookupRequestQueue = posCustomerLookupRequestQueue;
        this.posProductListRequestQueue = posProductListRequestQueue;
        this.posCashierLookupRequestQueue = posCashierLookupRequestQueue;
        this.posCashierByAdminLookupRequestQueue = posCashierByAdminLookupRequestQueue;
        this.productExchange = productExchange;
        this.customerExchange = customerExchange;
        this.posExchange = posExchange;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeQueuesAndBindings() {
        log.info("=== Initializing POS Module Queues and Bindings ===");

        try {
            // ── pos.variant.lookup.request ──────────────────────────────────
            rabbitAdmin.declareQueue(posVariantLookupRequestQueue);
            rabbitAdmin.declareExchange(productExchange);

            Binding variantBinding = BindingBuilder
                    .bind(posVariantLookupRequestQueue)
                    .to(productExchange)
                    .with("pos.variant.lookup.request");
            rabbitAdmin.declareBinding(variantBinding);
            log.info("✅ Binding declared: {} → {} [pos.variant.lookup.request]",
                    productExchange.getName(), posVariantLookupRequestQueue.getName());

            // ── pos.product.list.request ────────────────────────────────────
            rabbitAdmin.declareQueue(posProductListRequestQueue);

            Binding productListBinding = BindingBuilder
                    .bind(posProductListRequestQueue)
                    .to(productExchange)
                    .with("pos.product.list.request");
            rabbitAdmin.declareBinding(productListBinding);
            log.info("✅ Binding declared: {} → {} [pos.product.list.request]",
                    productExchange.getName(), posProductListRequestQueue.getName());

            // ── pos.customer.lookup.request ─────────────────────────────────
            rabbitAdmin.declareQueue(posCustomerLookupRequestQueue);
            rabbitAdmin.declareExchange(customerExchange);

            Binding customerBinding = BindingBuilder
                    .bind(posCustomerLookupRequestQueue)
                    .to(customerExchange)
                    .with("pos.customer.lookup.request");
            rabbitAdmin.declareBinding(customerBinding);
            log.info("✅ Binding declared: {} → {} [pos.customer.lookup.request]",
                    customerExchange.getName(), posCustomerLookupRequestQueue.getName());

            // ── pos.cashier.lookup.request ──────────────────────────────────
            rabbitAdmin.declareQueue(posCashierLookupRequestQueue);
            rabbitAdmin.declareExchange(posExchange);

            Binding cashierBinding = BindingBuilder
                    .bind(posCashierLookupRequestQueue)
                    .to(posExchange)
                    .with("pos.cashier.lookup.request");
            rabbitAdmin.declareBinding(cashierBinding);
            log.info("✅ Binding declared: {} → {} [pos.cashier.lookup.request]",
                    posExchange.getName(), posCashierLookupRequestQueue.getName());

            // ── pos.cashier.by.admin.lookup.request ─────────────────────────
            rabbitAdmin.declareQueue(posCashierByAdminLookupRequestQueue);

            Binding cashierByAdminBinding = BindingBuilder
                    .bind(posCashierByAdminLookupRequestQueue)
                    .to(posExchange)
                    .with("pos.cashier.by.admin.lookup.request");
            rabbitAdmin.declareBinding(cashierByAdminBinding);
            log.info("Binding declared: {} → {} [pos.cashier.by.admin.lookup.request]",
                    posExchange.getName(), posCashierByAdminLookupRequestQueue.getName());

            log.info("=== POS Module Queue Initialization Complete ===");

        } catch (Exception e) {
            log.error(" Failed to initialize POS module queues and bindings", e);
        }
    }
}
