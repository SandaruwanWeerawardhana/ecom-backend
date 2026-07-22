package org.psint.beyosclothing.modules.products.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ProductQueueInitializer {

    private final RabbitAdmin rabbitAdmin;
    private final Queue posProductListRequestQueue;
    private final TopicExchange productExchange;

    @Value("${app.rabbitmq.exchange.payment:beyos.exchange.payment}")
    private String paymentExchangeName;

    @Value("${app.rabbitmq.exchange.cart:beyos.exchange.cart}")
    private String cartExchangeName;

    @Value("${app.rabbitmq.queue.product-payment-methods-lookup-request:product.payment.methods.lookup.request}")
    private String productPaymentMethodsLookupQueueName;

    @Value("${app.rabbitmq.queue.product-payment-methods-names-lookup-request:product.payment.methods.names.lookup.request}")
    private String productPaymentMethodsNamesLookupQueueName;

    @Value("${app.rabbitmq.queue.product-payment-method-mapping-lookup-request:product.payment.method.mapping.lookup.request.queue}")
    private String productPaymentMethodMappingLookupQueueName;

    @Value("${app.rabbitmq.queue.cart-payment-method-lookup-request:cart.payment.method.lookup.request.queue}")
    private String cartPaymentMethodLookupQueueName;

    @Value("${app.rabbitmq.queue.inventory-product-lookup-request:inventory.product.lookup.request}")
    private String inventoryProductLookupRequestQueueName;

    @Value("${app.rabbitmq.queue.inventory-product-bulk-lookup-request:inventory.product.bulk.lookup.request}")
    private String inventoryProductBulkLookupRequestQueueName;

    public ProductQueueInitializer(
            RabbitAdmin rabbitAdmin,
            @Qualifier("posProductListRequestQueue") Queue posProductListRequestQueue,
            @Qualifier("productExchange") TopicExchange productExchange) {
        this.rabbitAdmin = rabbitAdmin;
        this.posProductListRequestQueue = posProductListRequestQueue;
        this.productExchange = productExchange;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeQueuesAndBindings() {
        log.info("=== Initializing Product Module Queues and Bindings ===");

        try {
            // ── POS product list queue ──────────────────────────────────────
            rabbitAdmin.declareQueue(posProductListRequestQueue);
            log.info("✅ Queue declared: {}", posProductListRequestQueue.getName());

            rabbitAdmin.declareExchange(productExchange);
            log.info("✅ Exchange declared: {}", productExchange.getName());

            Binding posBinding = BindingBuilder
                    .bind(posProductListRequestQueue)
                    .to(productExchange)
                    .with("pos.product.list.request");
            rabbitAdmin.declareBinding(posBinding);
            log.info("✅ Binding created: {} -> {} with routing key: pos.product.list.request",
                    productExchange.getName(), posProductListRequestQueue.getName());

            // ── Payment-module exchange (product module sends RPC to it) ──
            TopicExchange paymentExchange = new TopicExchange(paymentExchangeName, true, false);
            rabbitAdmin.declareExchange(paymentExchange);
            log.info("✅ Payment exchange declared: {}", paymentExchangeName);

            // ── product.payment.methods.lookup.request (consumed by payment module) ──
            Queue productPaymentMethodsLookupQueue = new Queue(productPaymentMethodsLookupQueueName, true);
            rabbitAdmin.declareQueue(productPaymentMethodsLookupQueue);
            Binding paymentLookupBinding = BindingBuilder
                    .bind(productPaymentMethodsLookupQueue)
                    .to(paymentExchange)
                    .with("product.payment.methods.lookup.request");
            rabbitAdmin.declareBinding(paymentLookupBinding);
            log.info("✅ Binding: {} -> {} [product.payment.methods.lookup.request]",
                    paymentExchangeName, productPaymentMethodsLookupQueueName);

            // ── product.payment.methods.names.lookup.request (consumed by payment module) ──
            Queue productPaymentMethodsNamesLookupQueue = new Queue(productPaymentMethodsNamesLookupQueueName, true);
            rabbitAdmin.declareQueue(productPaymentMethodsNamesLookupQueue);
            Binding paymentNamesLookupBinding = BindingBuilder
                    .bind(productPaymentMethodsNamesLookupQueue)
                    .to(paymentExchange)
                    .with("product.payment.methods.names.lookup.request");
            rabbitAdmin.declareBinding(paymentNamesLookupBinding);
            log.info("✅ Binding: {} -> {} [product.payment.methods.names.lookup.request]",
                    paymentExchangeName, productPaymentMethodsNamesLookupQueueName);

            // ── product.payment.method.mapping.lookup.request (consumed by product module) ──
            Queue productPaymentMethodMappingLookupQueue = new Queue(productPaymentMethodMappingLookupQueueName, true);
            rabbitAdmin.declareQueue(productPaymentMethodMappingLookupQueue);
            Binding mappingLookupBinding = BindingBuilder
                    .bind(productPaymentMethodMappingLookupQueue)
                    .to(productExchange)
                    .with("product.payment.method.mapping.lookup.request");
            rabbitAdmin.declareBinding(mappingLookupBinding);
            log.info("✅ Binding: {} -> {} [product.payment.method.mapping.lookup.request]",
                    productExchange.getName(), productPaymentMethodMappingLookupQueueName);

            // ── Cart-module exchange (payment module sends RPC to it) ──
            TopicExchange cartExchange = new TopicExchange(cartExchangeName, true, false);
            rabbitAdmin.declareExchange(cartExchange);
            log.info("✅ Cart exchange declared: {}", cartExchangeName);

            // ── cart.payment.method.lookup.request (consumed by cart module) ──
            Queue cartPaymentMethodLookupQueue = new Queue(cartPaymentMethodLookupQueueName, true);
            rabbitAdmin.declareQueue(cartPaymentMethodLookupQueue);
            Binding cartPaymentLookupBinding = BindingBuilder
                    .bind(cartPaymentMethodLookupQueue)
                    .to(cartExchange)
                    .with("cart.payment.method.lookup.request");
            rabbitAdmin.declareBinding(cartPaymentLookupBinding);
            log.info("✅ Binding: {} -> {} [cart.payment.method.lookup.request]",
                    cartExchangeName, cartPaymentMethodLookupQueueName);

            log.info("=== Product Module Initialization Complete ===");

            // ── Inventory ↔ Product Lookup Queue ──────────────────────────────
            Queue inventoryProductLookupQueue = new Queue(inventoryProductLookupRequestQueueName, true);
            rabbitAdmin.declareQueue(inventoryProductLookupQueue);
            Binding inventoryProductLookupBinding = BindingBuilder
                    .bind(inventoryProductLookupQueue)
                    .to(productExchange)
                    .with("inventory.product.lookup.request");
            rabbitAdmin.declareBinding(inventoryProductLookupBinding);
            log.info("✅ Binding: {} -> {} [inventory.product.lookup.request]",
                    productExchange.getName(), inventoryProductLookupRequestQueueName);

            Queue inventoryProductBulkLookupQueue = new Queue(inventoryProductBulkLookupRequestQueueName, true);
            rabbitAdmin.declareQueue(inventoryProductBulkLookupQueue);
            Binding inventoryProductBulkLookupBinding = BindingBuilder
                    .bind(inventoryProductBulkLookupQueue)
                    .to(productExchange)
                    .with("inventory.product.bulk.lookup.request");
            rabbitAdmin.declareBinding(inventoryProductBulkLookupBinding);
            log.info("Binding: {} -> {} [inventory.product.bulk.lookup.request]",
                    productExchange.getName(), inventoryProductBulkLookupRequestQueueName);

        } catch (Exception e) {
            log.error("❌ Failed to initialize product module queues and bindings", e);
        }
    }
}
