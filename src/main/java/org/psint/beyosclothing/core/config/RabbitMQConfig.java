package org.psint.beyosclothing.core.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.DefaultClassMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ Configuration
 * Configures exchanges, queues, and bindings for all modules
 */
@Configuration
@EnableRabbit
@SuppressWarnings("deprecation")
public class RabbitMQConfig {

    @Value("${app.rabbitmq.exchange.auth}")
    private String authExchange;

    @Value("${app.rabbitmq.exchange.admin}")
    private String adminExchange;

    @Value("${app.rabbitmq.exchange.product}")
    private String productExchange;

    @Value("${app.rabbitmq.exchange.inventory}")
    private String inventoryExchange;

    @Value("${app.rabbitmq.exchange.cart}")
    private String cartExchange;

    @Value("${app.rabbitmq.exchange.customer:beyos.exchange.customer}")
    private String customerExchange;

    @Value("${app.rabbitmq.exchange.promotion:beyos.exchange.promotion}")
    private String promotionExchange;

    @Value("${app.rabbitmq.exchange.payment:beyos.exchange.payment}")
    private String paymentExchange;

    @Value("${app.rabbitmq.exchange.delivery:beyos.exchange.delivery}")
    private String deliveryExchange;

    @Value("${app.rabbitmq.exchange.order:beyos.exchange.order}")
    private String orderExchange;

    @Value("${app.rabbitmq.exchange.pos-inventory:pos.inventory.exchange}")
    private String posInventoryExchange;

    @Value("${app.rabbitmq.exchange.pos:pos.exchange}")
    private String posExchange;

    @Value("${app.rabbitmq.exchange.pos-analytics:beyos.exchange.pos.analytics}")
    private String posAnalyticsExchange;

    // POS Module Queues
    @Value("${app.rabbitmq.queue.pos-product-sync:pos.product.sync.queue}")
    private String posProductSyncQueue;

    @Value("${app.rabbitmq.queue.pos-inventory-deduction:pos.inventory.deduction.queue}")
    private String posInventoryDeductionQueueName;

    @Value("${app.rabbitmq.queue.pos-order-events:pos.order.events.queue}")
    private String posOrderEventsQueue;

    // POS Dead Letter Queues
    @Value("${app.rabbitmq.queue.pos-product-sync-dlq:pos.product.sync.dlq}")
    private String posProductSyncDlq;

    @Value("${app.rabbitmq.queue.pos-inventory-deduction-dlq:pos.inventory.deduction.dlq}")
    private String posInventoryDeductionDlq;

    @Value("${app.rabbitmq.queue.pos-order-events-dlq:pos.order.events.dlq}")
    private String posOrderEventsDlq;

    // POS Retry Queues
    @Value("${app.rabbitmq.queue.pos-product-sync-retry-1:pos.product.sync.retry.1s}")
    private String posProductSyncRetry1;

    @Value("${app.rabbitmq.queue.pos-product-sync-retry-2:pos.product.sync.retry.5s}")
    private String posProductSyncRetry2;

    @Value("${app.rabbitmq.queue.pos-product-sync-retry-3:pos.product.sync.retry.30s}")
    private String posProductSyncRetry3;

    @Value("${app.rabbitmq.queue.pos-inventory-deduction-retry-1:pos.inventory.deduction.retry.1s}")
    private String posInventoryDeductionRetry1;

    @Value("${app.rabbitmq.queue.pos-inventory-deduction-retry-2:pos.inventory.deduction.retry.5s}")
    private String posInventoryDeductionRetry2;

    @Value("${app.rabbitmq.queue.pos-inventory-deduction-retry-3:pos.inventory.deduction.retry.30s}")
    private String posInventoryDeductionRetry3;

    @Value("${app.rabbitmq.queue.pos-order-events-retry-1:pos.order.events.retry.1s}")
    private String posOrderEventsRetry1;

    @Value("${app.rabbitmq.queue.pos-order-events-retry-2:pos.order.events.retry.5s}")
    private String posOrderEventsRetry2;

    @Value("${app.rabbitmq.queue.pos-order-events-retry-3:pos.order.events.retry.30s}")
    private String posOrderEventsRetry3;

    // POS Dead Letter Exchange
    @Value("${app.rabbitmq.exchange.pos-dlx:pos.dlx.exchange}")
    private String posDlxExchange;


    @Value("${app.rabbitmq.exchange.reseller:beyos.exchange.reseller}")
    private String resellerExchange;

    // Cross-Module Lookup Queues
    @Value("${app.rabbitmq.queue.customer-lookup-request:customer.lookup.request}")
    private String customerLookupRequestQueue;

    @Value("${app.rabbitmq.queue.customer-by-userid-request:customer.by.userid.request}")
    private String customerByUserIdRequestQueue;

    @Value("${app.rabbitmq.queue.user-email-lookup-request:user.email.lookup.request.queue}")
    private String userEmailLookupRequestQueue;

    @Value("${app.rabbitmq.queue.customer-details-request:customer.details.request.queue}")
    private String customerDetailsRequestQueue;

    @Value("${app.rabbitmq.queue.customer-search-request:customer.search.request}")
    private String customerSearchRequestQueue;

    @Value("${app.rabbitmq.queue.customer-simple-list-request:customer.simple.list.request}")
    private String customerSimpleListRequestQueue;

    @Value("${app.rabbitmq.queue.customer-update-request:customer.update.request}")
    private String customerUpdateRequestQueue;

    @Value("${app.rabbitmq.queue.product-lookup-request:product.lookup.request}")
    private String productLookupRequestQueue;

    @Value("${app.rabbitmq.queue.reseller-courier-lookup-request:reseller.courier.lookup.request.queue}")
    private String resellerCourierLookupRequestQueue;

    @Value("${app.rabbitmq.queue.reseller-cart-items-lookup-request:reseller.cart.items.lookup.request}")
    private String resellerCartItemsLookupRequestQueue;

    @Value("${app.rabbitmq.queue.reseller-orders-list-lookup-request:reseller.orders.list.lookup.request}")
    private String resellerOrdersListLookupRequestQueue;

    @Value("${app.rabbitmq.queue.reseller-order-delivered:reseller.order.delivered.cart.lookup}")
    private String resellerOrderDeliveredQueue;

//    @Value("${app.rabbitmq.queue.reseller-name-lookup-request:reseller.name.lookup.request}")
//    private String resellerNameLookupRequestQueue;

    @Value("${app.rabbitmq.queue.shipment-lookup-request:shipment.lookup.request.queue}")
    private String shipmentLookupRequestQueue;

    @Value("${app.rabbitmq.queue.shipment-create-or-get-request:shipment.create.or.get.request}")
    private String shipmentCreateOrGetRequestQueueName;

    @Value("${app.rabbitmq.queue.shipment-placed-with-courier:shipment.placed.with.courier}")
    private String shipmentPlacedWithCourierQueueName;

    @Value("${app.rabbitmq.queue.courier-api-call-log:courier.api.call.log}")
    private String courierApiCallLogQueueName;

    @Value("${app.rabbitmq.queue.shipment-status-update:shipment.status.update}")
    private String shipmentStatusUpdateQueueName;

    @Value("${app.rabbitmq.queue.product-details-lookup-request:product.details.lookup.request}")
    private String productDetailsLookupRequestQueue;

    @Value("${app.rabbitmq.queue.pos-product-list-request:pos.product.list.request}")
    private String posProductListRequestQueue;

    @Value("${app.rabbitmq.queue.pos-variant-lookup-request:pos.variant.lookup.request}")
    private String posVariantLookupRequestQueue;

    @Value("${app.rabbitmq.queue.pos-customer-lookup-request:pos.customer.lookup.request}")
    private String posCustomerLookupRequestQueue;

    @Value("${app.rabbitmq.queue.stock-check-request:inventory.stock.check}")
    private String stockCheckRequestQueue;

    @Value("${app.rabbitmq.queue.promo-validation-request:promotion.validate.request}")
    private String promoValidationRequestQueue;

    @Value("${app.rabbitmq.queue.product-discount-request:promotion.product.discount.request}")
    private String productDiscountRequestQueue;

    @Value("${app.rabbitmq.queue.payment-method-lookup-request:payment.method.lookup.request}")
    private String paymentMethodLookupRequestQueue;

    @Value("${app.rabbitmq.queue.payment-transaction-delivery-confirmed:payment.transaction.delivery.confirmed}")
    private String paymentTransactionDeliveryConfirmedQueue;

    @Value("${app.rabbitmq.queue.payment-status-updated-order:payment.status.updated.order}")
    private String paymentStatusUpdatedOrderQueue;

    @Value("${app.rabbitmq.queue.customer-created}")
    private String customerCreatedQueue;

    @Value("${app.rabbitmq.queue.customer-updated:customer.updated.queue}")
    private String customerUpdatedQueue;

    @Value("${app.rabbitmq.queue.customer-deleted:customer.deleted.queue}")
    private String customerDeletedQueue;

    @Value("${app.rabbitmq.queue.email-verification}")
    private String emailVerificationQueue;

    @Value("${app.rabbitmq.queue.password-reset}")
    private String passwordResetQueue;

    @Value("${app.rabbitmq.queue.admin-user-create}")
    private String adminUserCreateQueue;

    // RPC Queue for user creation requests (with response)
    private final String userCreateRpcQueue = "auth.user.create.rpc.queue";

    // RPC Queue for updating user account lock (with response)
    private final String userUpdateAccountLockedRpcQueue = "auth.user.update.account.locked.rpc.queue";

    @Value("${app.rabbitmq.queue.admin-user-role-update}")
    private String adminUserRoleUpdateQueue;

    @Value("${app.rabbitmq.queue.admin-permissions-initialize}")
    private String adminPermissionsInitializeQueue;

    @Value("${app.rabbitmq.queue.admin-role-create}")
    private String adminRoleCreateQueue;

    @Value("${app.rabbitmq.queue.admin-permissions-assign}")
    private String adminPermissionsAssignQueue;

    @Value("${app.rabbitmq.queue.admin-user-delete}")
    private String adminUserDeleteQueue;

    @Value("${app.rabbitmq.queue.admin-user-password-update:admin.user.password.update.queue}")
    private String adminUserPasswordUpdateQueue;

    @Value("${app.rabbitmq.queue.admin-user-details-update:admin.user.details.update.queue}")
    private String adminUserDetailsUpdateQueue;

    @Value("${app.rabbitmq.queue.admin-user-deactivate:admin.user.deactivate.queue}")
    private String adminUserDeactivateQueue;

    @Value("${app.rabbitmq.queue.auth-user-unlock:auth.user.unlock.queue}")
    private String authUserUnlockQueue;

    @Value("${app.rabbitmq.queue.auth-user-lock:auth.user.lock.queue}")
    private String authUserLockQueue;

    @Value("${app.rabbitmq.queue.auth-user-deactivate:auth.user.deactivate.queue}")
    private String authUserDeactivateQueue;

    // Auth RPC Password Queues
    @Value("${app.rabbitmq.queue.verify-password:auth.verify.password.queue}")
    private String authVerifyPasswordQueue;

    @Value("${app.rabbitmq.queue.update-password:auth.update.password.queue}")
    private String authUpdatePasswordQueue;

    // Product & Inventory Queues
    @Value("${app.rabbitmq.queue.product-created}")
    private String productCreatedQueue;

    @Value("${app.rabbitmq.queue.product-updated}")
    private String productUpdatedQueue;

    @Value("${app.rabbitmq.queue.product-deleted}")
    private String productDeletedQueue;

    @Value("${app.rabbitmq.queue.product-price-changed}")
    private String productPriceChangedQueue;

    // POS Product Sync Queues
    @Value("${app.rabbitmq.queue.pos-product-created:pos.product.created.queue}")
    private String posProductCreatedQueue;

    @Value("${app.rabbitmq.queue.pos-product-updated:pos.product.updated.queue}")
    private String posProductUpdatedQueue;

    @Value("${app.rabbitmq.queue.pos-product-deleted:pos.product.deleted.queue}")
    private String posProductDeletedQueue;

    @Value("${app.rabbitmq.queue.stock-updated}")
    private String stockUpdatedQueue;

    @Value("${app.rabbitmq.queue.stock-status-update:inventory.stock.status.update.queue}")
    private String stockStatusUpdateQueue;

    @Value("${app.rabbitmq.queue.stock-reserve-request}")
    private String stockReserveRequestQueue;

    @Value("${app.rabbitmq.queue.low-stock-alert}")
    private String lowStockAlertQueue;

    @Value("${app.rabbitmq.queue.variant-created}")
    private String variantCreatedQueue;

    // Cart Module Queues
    @Value("${app.rabbitmq.queue.cart-item-added}")
    private String cartItemAddedQueue;

    @Value("${app.rabbitmq.queue.cart-item-removed}")
    private String cartItemRemovedQueue;

    @Value("${app.rabbitmq.queue.cart-promo-applied}")
    private String cartPromoAppliedQueue;

    @Value("${app.rabbitmq.queue.cart-merged}")
    private String cartMergedQueue;

    @Value("${app.rabbitmq.queue.cart-expired}")
    private String cartExpiredQueue;

    // Checkout Module Queues (NEW - MISSING)
    @Value("${app.rabbitmq.queue.cart-checkout-request:${app.rabbitmq.queue.cart-items-checkout-request:cart.checkout.request.queue}}")
    private String cartCheckoutRequestQueue;

    @Value("${app.rabbitmq.queue.customer-address-lookup-request:customer.address.lookup.request.queue}")
    private String customerAddressLookupRequestQueue;

    @Value("${app.rabbitmq.queue.delivery-shipping-calculate-request:delivery.shipping.calculate.request.queue}")
    private String deliveryShippingCalculateRequestQueue;

    @Value("${app.rabbitmq.queue.delivery-courier-lookup-request:delivery.courier.lookup.request}")
    private String deliveryCourierLookupRequestQueue;

    // Order Module Queues
    @Value("${app.rabbitmq.queue.shipment-create-request:shipment.create.request.queue}")
    private String shipmentCreateRequestQueue;

    @Value("${app.rabbitmq.queue.payment-request-create-request:payment.request.create.request.queue}")
    private String paymentRequestCreateRequestQueue;

    @Value("${app.rabbitmq.queue.payment-status-verify-request:payment.status.verify.request.queue}")
    private String paymentStatusVerifyRequestQueue;

    @Value("${app.rabbitmq.queue.stock-decrease-request:inventory.stock.decrease.request.queue}")
    private String stockDecreaseRequestQueue;

    @Value("${app.rabbitmq.queue.cart-items-clear-request:cart.items.clear.request.queue}")
    private String cartItemsClearRequestQueue;

    // Reseller Module Queues
    @Value("${app.rabbitmq.queue.order-status-changed:order.status.changed.queue}")
    private String orderStatusChangedQueue;

    @Value("${app.rabbitmq.queue.payment-captured:payment.captured.queue}")
    private String paymentCapturedQueue;

    // Order Module Queues
    @Value("${app.rabbitmq.queue.order-create-request:order.create.request}")
    private String orderCreateRequestQueue;

    @Value("${app.rabbitmq.queue.order-detail-lookup-request:order.detail.lookup.request}")
    private String orderDetailLookupRequestQueue;

    @Value("${app.rabbitmq.queue.order-status-history-notes-lookup-request:order.status.history.notes.lookup.request}")
    private String orderStatusHistoryNotesLookupRequestQueue;

    @Value("${app.rabbitmq.queue.order-status-updated-courier:order.status.updated.courier}")
    private String orderStatusUpdatedCourierQueue;

    @Value("${app.rabbitmq.queue.order-delivery-completed:order.delivery.completed}")
    private String orderDeliveryCompletedQueue;

    @Value("${app.rabbitmq.queue.reseller-pending-orders-list-lookup-request:reseller.pending.orders.list.lookup.request}")
    private String resellerPendingOrdersListLookupRequestQueue;

    @Value("${app.rabbitmq.queue.admin-details-lookup-request:admin.details.lookup.request}")
    private String adminDetailsLookupRequestQueue;

    @Value("${app.rabbitmq.queue.admin-role-permission-lookup-request:admin.role.permission.lookup.request.queue}")
    private String adminRolePermissionLookupRequestQueue;

    @Value("${app.rabbitmq.queue.pos-cashier-lookup-request:pos.cashier.lookup.request}")
    private String posCashierLookupRequestQueue;

    @Value("${app.rabbitmq.queue.pos-cashier-by-admin-lookup-request:pos.cashier.by.admin.lookup.request}")
    private String posCashierByAdminLookupRequestQueue;

    @Value("${app.rabbitmq.queue.reseller-details-lookup-request:reseller.details.lookup.request}")
    private String resellerDetailsLookupRequestQueue;

    @Value("${app.rabbitmq.queue.reseller-product-detail-lookup-request:reseller.product.detail.lookup.request}")
    private String resellerProductDetailLookupRequestQueue;

    // ── Queues declared by ProductQueueInitializer (must be beans for listener startup) ──
    @Value("${app.rabbitmq.queue.product-payment-methods-lookup-request:product.payment.methods.lookup.request}")
    private String productPaymentMethodsLookupRequestQueue;

    @Value("${app.rabbitmq.queue.product-payment-methods-names-lookup-request:product.payment.methods.names.lookup.request}")
    private String productPaymentMethodsNamesLookupRequestQueue;

    @Value("${app.rabbitmq.queue.product-payment-method-mapping-lookup-request:product.payment.method.mapping.lookup.request.queue}")
    private String productPaymentMethodMappingLookupRequestQueue;

    @Value("${app.rabbitmq.queue.cart-payment-method-lookup-request:cart.payment.method.lookup.request.queue}")
    private String cartPaymentMethodLookupRequestQueue;

    // ── Inventory ↔ Product Lookup Queue ────────────────────────────────────
    @Value("${app.rabbitmq.queue.inventory-product-lookup-request:inventory.product.lookup.request}")
    private String inventoryProductLookupRequestQueue;

    @Value("${app.rabbitmq.queue.inventory-product-bulk-lookup-request:inventory.product.bulk.lookup.request}")
    private String inventoryProductBulkLookupRequestQueue;

    @Value("${app.rabbitmq.queue.shipment-pickup-requested:shipment.pickup.requested.order}")
    private String shipmentPickupRequestedOrderQueueName;

    @Value("${app.rabbitmq.queue.shipment-pickup-requested-delivery:shipment.pickup.requested.delivery}")
    private String shipmentPickupRequestedDeliveryQueueName;

    // ── RESELLER DASHBOARD & WALLET QUEUES (RPC) ───────────────────────────────────
    @Value("${app.rabbitmq.queue.reseller-dashboard-metrics-lookup-request:reseller.dashboard.metrics.lookup.request}")
    private String resellerDashboardMetricsLookupRequestQueue;

    @Value("${app.rabbitmq.queue.customer-order-counts-lookup-request:customer.order.counts.lookup.request}")
    private String customerOrderCountsLookupRequestQueue;

    @Value("${app.rabbitmq.queue.reseller-wallet-summary-lookup-request:reseller.wallet.summary.lookup.request}")
    private String resellerWalletSummaryLookupRequestQueue;

    // ========================================
    // EXCHANGES
    // ========================================

    @Bean
    public TopicExchange authExchange() {
        return new TopicExchange(authExchange);
    }

    @Bean
    public TopicExchange adminExchange() {
        return new TopicExchange(adminExchange);
    }

    @Bean
    public TopicExchange productExchange() {
        return new TopicExchange(productExchange);
    }

    @Bean
    public TopicExchange inventoryExchange() {
        return new TopicExchange(inventoryExchange);
    }

    @Bean
    public TopicExchange cartExchange() {
        return new TopicExchange(cartExchange);
    }

    @Bean
    public TopicExchange customerExchange() {
        return new TopicExchange(customerExchange);
    }

    @Bean
    public TopicExchange promotionExchange() {
        return new TopicExchange(promotionExchange);
    }

    @Bean
    public TopicExchange paymentExchange() {
        return new TopicExchange(paymentExchange);
    }

    @Bean
    public TopicExchange deliveryExchange() {
        return new TopicExchange(deliveryExchange);
    }

    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange(orderExchange);
    }

    @Bean
    public TopicExchange resellerExchange() {
        return new TopicExchange(resellerExchange);
    }

    @Bean
    public TopicExchange posInventoryExchange() {
        return new TopicExchange(posInventoryExchange);
    }

    @Bean
    public TopicExchange posExchange() {
        return new TopicExchange(posExchange, true, false);
    }

    @Bean
    public TopicExchange posDlxExchange() {
        return new TopicExchange(posDlxExchange, true, false);
    }

    @Bean
    public TopicExchange posAnalyticsExchange() {
        return new TopicExchange(posAnalyticsExchange, true, false);
    }

    // ========================================
    // QUEUES - Auth Module
    // ========================================

    @Bean
    public Queue customerCreatedQueue() {
        return QueueBuilder.durable(customerCreatedQueue).build();
    }

    @Bean
    public Queue customerUpdatedQueue() {
        return QueueBuilder.durable(customerUpdatedQueue).build();
    }

    @Bean
    public Queue customerDeletedQueue() {
        return QueueBuilder.durable(customerDeletedQueue).build();
    }

    @Bean
    public Queue emailVerificationQueue() {
        return QueueBuilder.durable(emailVerificationQueue).build();
    }

    @Bean
    public Queue passwordResetQueue() {
        return QueueBuilder.durable(passwordResetQueue).build();
    }

    @Bean
    public Queue authVerifyPasswordQueue() {
        return QueueBuilder.durable(authVerifyPasswordQueue).build();
    }

    @Bean
    public Queue authUpdatePasswordQueue() {
        return QueueBuilder.durable(authUpdatePasswordQueue).build();
    }

    @Bean
    public Queue userUpdateAccountLockedRpcQueue() {
        return QueueBuilder.durable(userUpdateAccountLockedRpcQueue).build();
    }

    @Bean
    public Binding userCreateRpcBinding() {
        return BindingBuilder
                .bind(userCreateRpcQueue())
                .to(authExchange())
                .with("auth.user.create.rpc");
    }

    @Bean
    public Binding authVerifyPasswordBinding() {
        return BindingBuilder
                .bind(authVerifyPasswordQueue())
                .to(authExchange())
                .with("auth.user.verify.password.rpc");
    }

    @Bean
    public Binding authUpdatePasswordBinding() {
        return BindingBuilder
                .bind(authUpdatePasswordQueue())
                .to(authExchange())
                .with("auth.user.update.password.rpc");
    }

    @Bean
    public Binding userUpdateAccountLockedRpcBinding() {
        return BindingBuilder
                .bind(userUpdateAccountLockedRpcQueue())
                .to(authExchange())
                .with("auth.user.update.account.locked.rpc");
    }

    // ── Product ↔ Payment Lookup Queues (from ProductQueueInitializer) ──────

    @Bean
    public Queue productPaymentMethodsLookupRequestQueue() {
        return QueueBuilder.durable(productPaymentMethodsLookupRequestQueue).build();
    }

    @Bean
    public Binding productPaymentMethodsLookupRequestBinding() {
        return BindingBuilder
                .bind(productPaymentMethodsLookupRequestQueue())
                .to(paymentExchange())
                .with("product.payment.methods.lookup.request");
    }

    @Bean
    public Queue productPaymentMethodsNamesLookupRequestQueue() {
        return QueueBuilder.durable(productPaymentMethodsNamesLookupRequestQueue).build();
    }

    @Bean
    public Binding productPaymentMethodsNamesLookupRequestBinding() {
        return BindingBuilder
                .bind(productPaymentMethodsNamesLookupRequestQueue())
                .to(paymentExchange())
                .with("product.payment.methods.names.lookup.request");
    }

    @Bean
    public Queue productPaymentMethodMappingLookupRequestQueue() {
        return QueueBuilder.durable(productPaymentMethodMappingLookupRequestQueue).build();
    }

    @Bean
    public Binding productPaymentMethodMappingLookupRequestBinding() {
        return BindingBuilder
                .bind(productPaymentMethodMappingLookupRequestQueue())
                .to(productExchange())
                .with("product.payment.method.mapping.lookup.request");
    }

// ── Cart ↔ Payment Lookup Queue (from ProductQueueInitializer) ──────────

    @Bean
    public Queue cartPaymentMethodLookupRequestQueue() {
        return QueueBuilder.durable(cartPaymentMethodLookupRequestQueue).build();
    }

    @Bean
    public Binding cartPaymentMethodLookupRequestBinding() {
        return BindingBuilder
                .bind(cartPaymentMethodLookupRequestQueue())
                .to(cartExchange())
                .with("cart.payment.method.lookup.request");
    }

    @Bean
    public Binding cartItemsClearRequestBinding() {
        return BindingBuilder
                .bind(cartItemsClearRequestQueue())
                .to(cartExchange())
                .with("cart.items.clear.request");
    }

    // ── Inventory ↔ Product Lookup Queue ─────────────────────────────────────

    @Bean
    public Queue inventoryProductLookupRequestQueue() {
        return QueueBuilder.durable(inventoryProductLookupRequestQueue).build();
    }

    @Bean
    public Binding inventoryProductLookupRequestBinding() {
        return BindingBuilder
                .bind(inventoryProductLookupRequestQueue())
                .to(productExchange())
                .with("inventory.product.lookup.request");
    }

    @Bean
    public Queue inventoryProductBulkLookupRequestQueue() {
        return QueueBuilder.durable(inventoryProductBulkLookupRequestQueue).build();
    }

    @Bean
    public Binding inventoryProductBulkLookupRequestBinding() {
        return BindingBuilder
                .bind(inventoryProductBulkLookupRequestQueue())
                .to(productExchange())
                .with("inventory.product.bulk.lookup.request");
    }

    // ========================================
    // QUEUES - Admin Module Events (consumed by Auth)
    // ========================================

    @Bean
    public Queue adminUserCreateQueue() {
        return QueueBuilder.durable(adminUserCreateQueue).build();
    }

    // RPC Queue for user creation requests (with response)
    @Bean
    public Queue userCreateRpcQueue() {
        return QueueBuilder.durable(userCreateRpcQueue).build();
    }

    @Bean
    public Queue adminUserRoleUpdateQueue() {
        return QueueBuilder.durable(adminUserRoleUpdateQueue).build();
    }

    @Bean
    public Queue adminPermissionsInitializeQueue() {
        return QueueBuilder.durable(adminPermissionsInitializeQueue).build();
    }

    @Bean
    public Queue adminRoleCreateQueue() {
        return QueueBuilder.durable(adminRoleCreateQueue).build();
    }

    @Bean
    public Queue adminPermissionsAssignQueue() {
        return QueueBuilder.durable(adminPermissionsAssignQueue).build();
    }

    @Bean
    public Queue adminUserDeleteQueue() {
        return QueueBuilder.durable(adminUserDeleteQueue).build();
    }

    @Bean
    public Queue adminUserPasswordUpdateQueue() {
        return QueueBuilder.durable(adminUserPasswordUpdateQueue).build();
    }

    @Bean
    public Queue adminUserDetailsUpdateQueue() {
        return QueueBuilder.durable(adminUserDetailsUpdateQueue).build();
    }

    @Bean
    public Queue adminUserDeactivateQueue() {
        return QueueBuilder.durable(adminUserDeactivateQueue).build();
    }

    @Bean
    public Queue authUserUnlockQueue() {
        return QueueBuilder.durable(authUserUnlockQueue).build();
    }




    @Bean
    public Queue authUserLockQueue() {
        return QueueBuilder.durable(authUserLockQueue).build();
    }

    @Bean
    public Queue authUserDeactivateQueue() {
        return QueueBuilder.durable(authUserDeactivateQueue).build();
    }

    // Product & Inventory Queues
    @Bean
    public Queue productCreatedQueue() {
        return QueueBuilder.durable(productCreatedQueue).build();
    }

    @Bean
    public Binding productCreatedBinding() {
        return BindingBuilder
                .bind(productCreatedQueue())
                .to(productExchange())
                .with("product.created");
    }

    @Bean
    public Queue productUpdatedQueue() {
        return QueueBuilder.durable(productUpdatedQueue).build();
    }

    @Bean
    public Binding productUpdatedBinding() {
        return BindingBuilder
                .bind(productUpdatedQueue())
                .to(productExchange())
                .with("product.updated");
    }

    @Bean
    public Queue productDeletedQueue() {
        return QueueBuilder.durable(productDeletedQueue).build();
    }

    @Bean
    public Binding productDeletedBinding() {
        return BindingBuilder
                .bind(productDeletedQueue())
                .to(productExchange())
                .with("product.deleted");
    }

    @Bean
    public Queue productPriceChangedQueue() {
        return QueueBuilder.durable(productPriceChangedQueue).build();
    }

    @Bean
    public Binding productPriceChangedBinding() {
        return BindingBuilder
                .bind(productPriceChangedQueue())
                .to(productExchange())
                .with("product.price.changed");
    }

    @Bean
    public Queue variantCreatedQueue() {
        return QueueBuilder.durable(variantCreatedQueue).build();
    }

    @Bean
    public Binding variantCreatedBinding() {
        return BindingBuilder
                .bind(variantCreatedQueue())
                .to(productExchange())
                .with("variant.created");
    }

    // POS Product Sync Queues
    @Bean
    public Queue posProductCreatedQueue() {
        return QueueBuilder.durable(posProductCreatedQueue).build();
    }

    @Bean
    public Binding adminUserCreateBinding() {
        return BindingBuilder
                .bind(adminUserCreateQueue())
                .to(adminExchange())
                .with("admin.user.create");
    }


    @Bean
    public Binding adminUserRoleUpdateBinding() {
        return BindingBuilder
                .bind(adminUserRoleUpdateQueue())
                .to(adminExchange())
                .with("admin.user.role.update");
    }

    @Bean
    public Binding adminPermissionsInitializeBinding() {
        return BindingBuilder
                .bind(adminPermissionsInitializeQueue())
                .to(adminExchange())
                .with("admin.permissions.initialize");
    }

    @Bean
    public Binding adminRoleCreateBinding() {
        return BindingBuilder
                .bind(adminRoleCreateQueue())
                .to(adminExchange())
                .with("admin.role.create");
    }

    @Bean
    public Binding adminPermissionsAssignBinding() {
        return BindingBuilder
                .bind(adminPermissionsAssignQueue())
                .to(adminExchange())
                .with("admin.permissions.assign");
    }

    @Bean
    public Binding adminUserDeleteBinding() {
        return BindingBuilder
                .bind(adminUserDeleteQueue())
                .to(adminExchange())
                .with("admin.user.delete");
    }

    @Bean
    public Binding adminUserPasswordUpdateBinding() {
        return BindingBuilder
                .bind(adminUserPasswordUpdateQueue())
                .to(adminExchange())
                .with("admin.user.password.update");
    }

    @Bean
    public Binding adminUserDetailsUpdateBinding() {
        return BindingBuilder
                .bind(adminUserDetailsUpdateQueue())
                .to(adminExchange())
                .with("admin.user.details.update");
    }

    @Bean
    public Binding adminUserDeactivateBinding() {
        return BindingBuilder
                .bind(adminUserDeactivateQueue())
                .to(adminExchange())
                .with("admin.user.deactivate");
    }

    @Bean
    public Binding authUserDeactivateBinding() {
        return BindingBuilder
                .bind(authUserDeactivateQueue())
                .to(authExchange())
                .with("auth.user.deactivate");
    }



    @Bean
    public Binding posProductCreatedBinding() {
        return BindingBuilder
                .bind(posProductCreatedQueue())
                .to(productExchange())
                .with("product.created");
    }

    @Bean
    public Queue posProductUpdatedQueue() {
        return QueueBuilder.durable(posProductUpdatedQueue).build();
    }

    @Bean
    public Binding posProductUpdatedBinding() {
        return BindingBuilder
                .bind(posProductUpdatedQueue())
                .to(productExchange())
                .with("product.updated");
    }

    @Bean
    public Queue posProductDeletedQueue() {
        return QueueBuilder.durable(posProductDeletedQueue).build();
    }

    @Bean
    public Binding posProductDeletedBinding() {
        return BindingBuilder
                .bind(posProductDeletedQueue())
                .to(productExchange())
                .with("product.deleted");
    }

    /**
     * Bind posProductSyncQueue to productExchange to receive product lifecycle events.
     * This queue is used by PosProductSyncListener to handle all product events (created, updated, deleted).
     */
    @Bean
    public Binding posProductSyncCreatedBinding() {
        return BindingBuilder
                .bind(posProductSyncQueue())
                .to(productExchange())
                .with("product.created");
    }

    @Bean
    public Binding posProductSyncUpdatedBinding() {
        return BindingBuilder
                .bind(posProductSyncQueue())
                .to(productExchange())
                .with("product.updated");
    }

    @Bean
    public Binding posProductSyncDeletedBinding() {
        return BindingBuilder
                .bind(posProductSyncQueue())
                .to(productExchange())
                .with("product.deleted");
    }

    @Bean
    public Queue orderCreateRequestQueue() {
        return QueueBuilder.durable(orderCreateRequestQueue).build();
    }

    @Bean
    public Queue orderDetailLookupRequestQueue() {
        return QueueBuilder.durable(orderDetailLookupRequestQueue).build();
    }

    @Bean
    public Queue orderStatusHistoryNotesLookupRequestQueue() {
        return QueueBuilder.durable(orderStatusHistoryNotesLookupRequestQueue).build();
    }

    @Bean
    public Queue orderStatusUpdatedCourierQueue() {
        return QueueBuilder.durable(orderStatusUpdatedCourierQueue).build();
    }

    @Bean
    public Queue orderDeliveryCompletedQueue() {
        return QueueBuilder.durable(orderDeliveryCompletedQueue).build();
    }

    @Bean
    public Queue resellerPendingOrdersListLookupRequestQueue() {
        return QueueBuilder.durable(resellerPendingOrdersListLookupRequestQueue).build();
    }

    @Bean
    public Binding resellerPendingOrdersListLookupRequestBinding() {
        return BindingBuilder
                .bind(resellerPendingOrdersListLookupRequestQueue())
                .to(orderExchange())
                .with("reseller.pending.orders.list.lookup.request");
    }

    @Bean
    public Queue stockUpdatedQueue() {
        return QueueBuilder.durable(stockUpdatedQueue).build();
    }

    @Bean
    public Queue stockStatusUpdateQueue() {
        return QueueBuilder.durable(stockStatusUpdateQueue).build();
    }

    @Bean
    public Queue stockReserveRequestQueue() {
        return QueueBuilder.durable(stockReserveRequestQueue).build();
    }

    @Bean
    public Queue lowStockAlertQueue() {
        return QueueBuilder.durable(lowStockAlertQueue).build();
    }

    // Cart Module Queues
    @Bean
    public Queue cartItemAddedQueue() {
        return QueueBuilder.durable(cartItemAddedQueue).build();
    }

    @Bean
    public Queue cartItemRemovedQueue() {
        return QueueBuilder.durable(cartItemRemovedQueue).build();
    }

    @Bean
    public Queue cartPromoAppliedQueue() {
        return QueueBuilder.durable(cartPromoAppliedQueue).build();
    }

    @Bean
    public Queue cartMergedQueue() {
        return QueueBuilder.durable(cartMergedQueue).build();
    }

    @Bean
    public Queue cartExpiredQueue() {
        return QueueBuilder.durable(cartExpiredQueue).build();
    }

    // Checkout Module Queues (NEW - MISSING)
    @Bean
    public Queue cartCheckoutRequestQueue() {
        return QueueBuilder.durable(cartCheckoutRequestQueue).build();
    }

    @Bean
    public Queue customerAddressLookupRequestQueue() {
        return QueueBuilder.durable(customerAddressLookupRequestQueue).build();
    }

    @Bean
    public Queue deliveryShippingCalculateRequestQueue() {
        return QueueBuilder.durable(deliveryShippingCalculateRequestQueue).build();
    }

    @Bean
    public Queue deliveryCourierLookupRequestQueue() {
        return QueueBuilder.durable(deliveryCourierLookupRequestQueue).build();
    }

    // Order Module Queues
    @Bean
    public Queue shipmentCreateRequestQueue() {
        return QueueBuilder.durable(shipmentCreateRequestQueue).build();
    }

    @Bean
    public Queue paymentRequestCreateRequestQueue() {
        return QueueBuilder.durable(paymentRequestCreateRequestQueue).build();
    }

    @Bean
    public Queue paymentStatusVerifyRequestQueue() {
        return QueueBuilder.durable(paymentStatusVerifyRequestQueue).build();
    }

    @Bean
    public Queue stockDecreaseRequestQueue() {
        return QueueBuilder.durable(stockDecreaseRequestQueue).build();
    }

    @Bean
    public Queue cartItemsClearRequestQueue() {
        return QueueBuilder.durable(cartItemsClearRequestQueue).build();
    }

    // Reseller Module Queues
    @Bean
    public Queue orderStatusChangedQueue() {
        return QueueBuilder.durable(orderStatusChangedQueue).build();
    }

    @Bean
    public Queue paymentCapturedQueue() {
        return QueueBuilder.durable(paymentCapturedQueue).build();
    }


    @Bean
    public Queue shipmentPickupRequestedOrderQueue() {
        return QueueBuilder.durable(shipmentPickupRequestedOrderQueueName).build();
    }

    @Bean
    public Queue shipmentPickupRequestedDeliveryQueue() {
        return QueueBuilder.durable(shipmentPickupRequestedDeliveryQueueName).build();
    }

    // ========================================
    // BINDINGS - Auth Module
    // ========================================

    @Bean
    public Binding customerCreatedBinding() {
        return BindingBuilder
                .bind(customerCreatedQueue())
                .to(authExchange())
                .with("auth.user.created");
    }

    @Bean
    public Binding customerUpdatedBinding() {
        return BindingBuilder
                .bind(customerUpdatedQueue())
                .to(customerExchange())
                .with("customer.updated");
    }

    @Bean
    public Binding customerDeletedBinding() {
        return BindingBuilder
                .bind(customerDeletedQueue())
                .to(customerExchange())
                .with("customer.deleted");
    }

    @Bean
    public Binding shipmentPickupRequestedOrderBinding() {
        return BindingBuilder
                .bind(shipmentPickupRequestedOrderQueue())
                .to(orderExchange())
                .with("shipment.pickup.requested");
    }

    @Bean
    public Binding shipmentPickupRequestedDeliveryBinding() {
        return BindingBuilder
                .bind(shipmentPickupRequestedDeliveryQueue())
                .to(deliveryExchange())
                .with("shipment.pickup.requested");
    }

    // ========================================
    // QUEUES - Cross-Module Lookup
    // ========================================

    @Bean
    public Queue customerLookupRequestQueue() {
        return QueueBuilder.durable(customerLookupRequestQueue).build();
    }

    @Bean
    public Queue customerByUserIdRequestQueue() {
        return QueueBuilder.durable(customerByUserIdRequestQueue).build();
    }

    @Bean
    public Queue userEmailLookupRequestQueue() {
        return QueueBuilder.durable(userEmailLookupRequestQueue).build();
    }

    @Bean
    public Binding userEmailLookupRequestBinding() {
        return BindingBuilder
                .bind(userEmailLookupRequestQueue())
                .to(authExchange())
                .with("user.email.lookup.request");
    }

    @Bean
    public Binding customerByUserIdRequestBinding() {
        return BindingBuilder
                .bind(customerByUserIdRequestQueue())
                .to(customerExchange())
                .with("customer.by.userid.request");
    }

    @Bean
    public Queue customerDetailsRequestQueue() {
        return QueueBuilder.durable(customerDetailsRequestQueue).build();
    }

    @Bean
    public Queue customerSearchRequestQueue() {
        return QueueBuilder.durable(customerSearchRequestQueue).build();
    }

    @Bean
    public Binding customerLookupRequestBinding() {
        return BindingBuilder
                .bind(customerLookupRequestQueue())
                .to(customerExchange())
                .with("customer.lookup.request");
    }

    @Bean
    public Binding customerDetailsRequestBinding() {
        return BindingBuilder
                .bind(customerDetailsRequestQueue())
                .to(customerExchange())
                .with("customer.details.request");
    }

    @Bean
    public Binding customerSearchRequestBinding() {
        return BindingBuilder
                .bind(customerSearchRequestQueue())
                .to(customerExchange())
                .with("customer.search.request");
    }

    @Bean
    public Queue customerSimpleListRequestQueue() {
        return QueueBuilder.durable(customerSimpleListRequestQueue).build();
    }

    @Bean
    public Queue customerUpdateRequestQueue() {
        return QueueBuilder.durable(customerUpdateRequestQueue).build();
    }

    @Bean
    public Binding customerUpdateRequestBinding() {
        return BindingBuilder
                .bind(customerUpdateRequestQueue())
                .to(customerExchange())
                .with("customer.update.request");
    }

    @Bean
    public Queue productLookupRequestQueue() {
        return QueueBuilder.durable(productLookupRequestQueue).build();
    }

    @Bean
    public Binding productLookupRequestBinding() {
        return BindingBuilder
                .bind(productLookupRequestQueue())
                .to(productExchange())
                .with("product.lookup.request");
    }

    @Bean
    public Queue productDetailsLookupRequestQueue() {
        return QueueBuilder.durable(productDetailsLookupRequestQueue).build();
    }

    @Bean
    public Binding productDetailsLookupRequestBinding() {
        return BindingBuilder
                .bind(productDetailsLookupRequestQueue())
                .to(productExchange())
                .with("product.details.lookup.request");
    }

    @Bean
    public Queue posProductListRequestQueue() {
        return QueueBuilder.durable(posProductListRequestQueue).build();
    }

    @Bean
    public Queue posVariantLookupRequestQueue() {
        return QueueBuilder.durable(posVariantLookupRequestQueue).build();
    }

    @Bean
    public Queue posCustomerLookupRequestQueue() {
        return QueueBuilder.durable(posCustomerLookupRequestQueue).build();
    }

    @Bean
    public Queue stockCheckRequestQueue() {
        return QueueBuilder.durable(stockCheckRequestQueue).build();
    }

    @Bean
    public Binding stockCheckRequestBinding() {
        return BindingBuilder
                .bind(stockCheckRequestQueue())
                .to(inventoryExchange())
                .with("inventory.stock.check");
    }

    @Bean
    public Queue stockReserveRequestQueueBean() {
        return QueueBuilder.durable(stockReserveRequestQueue).build();
    }

    @Bean
    public Binding stockReserveRequestBinding() {
        return BindingBuilder
                .bind(stockReserveRequestQueueBean())
                .to(inventoryExchange())
                .with("inventory.stock.reserve.request");
    }

    @Bean
    public Queue promoValidationRequestQueue() {
        return QueueBuilder.durable(promoValidationRequestQueue).build();
    }

    @Bean
    public Queue productDiscountRequestQueue() {
        return QueueBuilder.durable(productDiscountRequestQueue).build();
    }

    @Bean
    public Queue paymentMethodLookupRequestQueue() {
        return QueueBuilder.durable(paymentMethodLookupRequestQueue).build();
    }

    @Bean
    public Queue paymentTransactionDeliveryConfirmedQueue() {
        return QueueBuilder.durable(paymentTransactionDeliveryConfirmedQueue).build();
    }

    @Bean
    public Queue paymentStatusUpdatedOrderQueue() {
        return QueueBuilder.durable(paymentStatusUpdatedOrderQueue).build();
    }

    @Bean
    public Binding paymentStatusUpdatedOrderBinding() {
        return BindingBuilder
                .bind(paymentStatusUpdatedOrderQueue())
                .to(paymentExchange())
                .with("payment.status.updated");
    }


    // ========================================
    // QUEUES - POS Inventory Deduction
    // ========================================

//    @Bean
//    public Queue posInventoryDeductionQueue() {
//        return QueueBuilder.durable("pos.inventory.deduction.queue").build();
//    }

    @Bean
    public Queue posInventoryConfirmationQueue() {
        return QueueBuilder.durable("pos.inventory.confirmation.queue").build();
    }

    // ========================================
    // QUEUES - POS Module (Production-Ready with Retry & DLQ)
    // ========================================

    /**
     * POS Product Sync Queue - Main queue for product update events
     * Receives: product.created, product.updated, product.deleted
     * DLX: pos.dlx.exchange
     * Max retries: 3 (1s, 5s, 30s)
     */
    @Bean
    public Queue posProductSyncQueue() {
        return QueueBuilder.durable(posProductSyncQueue)
                .withArgument("x-dead-letter-exchange", posDlxExchange)
                .withArgument("x-dead-letter-routing-key", "pos.product.sync.dlq")
                .build();
    }

    @Bean
    public Queue posProductSyncRetry1Queue() {
        return QueueBuilder.durable(posProductSyncRetry1)
                .withArgument("x-dead-letter-exchange", posExchange)
                .withArgument("x-dead-letter-routing-key", "pos.product.sync")
                .withArgument("x-message-ttl", 1000) // 1 second
                .build();
    }

    @Bean
    public Queue posProductSyncRetry2Queue() {
        return QueueBuilder.durable(posProductSyncRetry2)
                .withArgument("x-dead-letter-exchange", posExchange)
                .withArgument("x-dead-letter-routing-key", "pos.product.sync")
                .withArgument("x-message-ttl", 5000) // 5 seconds
                .build();
    }

    @Bean
    public Queue posProductSyncRetry3Queue() {
        return QueueBuilder.durable(posProductSyncRetry3)
                .withArgument("x-dead-letter-exchange", posExchange)
                .withArgument("x-dead-letter-routing-key", "pos.product.sync")
                .withArgument("x-message-ttl", 30000) // 30 seconds
                .build();
    }

    @Bean
    public Queue posProductSyncDlqQueue() {
        return QueueBuilder.durable(posProductSyncDlq).build();
    }

    /**
     * POS Inventory Deduction Queue - Stock deduction requests
     * Handles async inventory deduction after POS order placement
     * DLX: pos.dlx.exchange
     * Max retries: 3 (1s, 5s, 30s)
     */
    @Bean
    public Queue posInventoryDeductionMainQueue() {
        return QueueBuilder.durable(posInventoryDeductionQueueName)
                .withArgument("x-dead-letter-exchange", posDlxExchange)
                .withArgument("x-dead-letter-routing-key", "pos.inventory.deduction.dlq")
                .build();
    }

    @Bean
    public Queue posInventoryDeductionRetry1Queue() {
        return QueueBuilder.durable(posInventoryDeductionRetry1)
                .withArgument("x-dead-letter-exchange", posExchange)
                .withArgument("x-dead-letter-routing-key", "pos.inventory.deduction")
                .withArgument("x-message-ttl", 1000) // 1 second
                .build();
    }

    @Bean
    public Queue posInventoryDeductionRetry2Queue() {
        return QueueBuilder.durable(posInventoryDeductionRetry2)
                .withArgument("x-dead-letter-exchange", posExchange)
                .withArgument("x-dead-letter-routing-key", "pos.inventory.deduction")
                .withArgument("x-message-ttl", 5000) // 5 seconds
                .build();
    }

    @Bean
    public Queue posInventoryDeductionRetry3Queue() {
        return QueueBuilder.durable(posInventoryDeductionRetry3)
                .withArgument("x-dead-letter-exchange", posExchange)
                .withArgument("x-dead-letter-routing-key", "pos.inventory.deduction")
                .withArgument("x-message-ttl", 30000) // 30 seconds
                .build();
    }

    @Bean
    public Queue posInventoryDeductionDlqQueue() {
        return QueueBuilder.durable(posInventoryDeductionDlq).build();
    }

    /**
     * POS Order Events Queue - Order placement events for analytics
     * Publishes: POS order created, completed events
     * DLX: pos.dlx.exchange
     * Max retries: 3 (1s, 5s, 30s)
     */
    @Bean
    public Queue posOrderEventsMainQueue() {
        return QueueBuilder.durable(posOrderEventsQueue)
                .withArgument("x-dead-letter-exchange", posDlxExchange)
                .withArgument("x-dead-letter-routing-key", "pos.order.events.dlq")
                .build();
    }

    @Bean
    public Queue posOrderEventsRetry1Queue() {
        return QueueBuilder.durable(posOrderEventsRetry1)
                .withArgument("x-dead-letter-exchange", posExchange)
                .withArgument("x-dead-letter-routing-key", "pos.order.events")
                .withArgument("x-message-ttl", 1000) // 1 second
                .build();
    }

    @Bean
    public Queue posOrderEventsRetry2Queue() {
        return QueueBuilder.durable(posOrderEventsRetry2)
                .withArgument("x-dead-letter-exchange", posExchange)
                .withArgument("x-dead-letter-routing-key", "pos.order.events")
                .withArgument("x-message-ttl", 5000) // 5 seconds
                .build();
    }

    @Bean
    public Queue posOrderEventsRetry3Queue() {
        return QueueBuilder.durable(posOrderEventsRetry3)
                .withArgument("x-dead-letter-exchange", posExchange)
                .withArgument("x-dead-letter-routing-key", "pos.order.events")
                .withArgument("x-message-ttl", 30000) // 30 seconds
                .build();
    }

    @Bean
    public Queue posOrderEventsDlqQueue() {
        return QueueBuilder.durable(posOrderEventsDlq).build();
    }


    // ========================================
    // QUEUES - Delivery Module (Shipment & Courier)
    // ========================================

    @Bean
    public Queue shipmentLookupRequestQueue() {
        return QueueBuilder.durable(shipmentLookupRequestQueue).build();
    }

    @Bean
    public Queue shipmentCreateOrGetRequestQueue() {
        return QueueBuilder.durable(shipmentCreateOrGetRequestQueueName).build();
    }

    @Bean
    public Queue courierApiCallLogQueue() {
        return QueueBuilder.durable(courierApiCallLogQueueName).build();
    }

    @Bean
    public Queue shipmentStatusUpdateQueue() {
        return QueueBuilder.durable(shipmentStatusUpdateQueueName).build();
    }

    @Bean
    public Queue shipmentPickupRequestedQueue() {
        return QueueBuilder.durable("shipment.pickup.requested").build();
    }

    // ========================================
    // MESSAGE CONVERTER & TEMPLATE
    // ========================================

    @Bean
    @SuppressWarnings("deprecation")
    public MessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        DefaultClassMapper classMapper = new DefaultClassMapper();
        // Trust all packages (since messages only come from our own services)
        classMapper.setTrustedPackages("*");
        converter.setClassMapper(classMapper);
        return converter;
    }

    @Bean(name = "rabbitListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            @Qualifier("jsonMessageConverter") MessageConverter messageConverter) {

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setConcurrentConsumers(3);
        factory.setMaxConcurrentConsumers(10);
        factory.setPrefetchCount(10);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setDefaultRequeueRejected(false);

        return factory;
    }

    /**
     * Reply timeout (ms) for request-reply calls (convertSendAndReceive). The default of 5s is too tight
     * for chained cross-module lookups and for the payment RPC that performs an outbound OnePay HTTP call,
     * causing premature "did not return a response" nulls. Overridable via app.rabbitmq.template.reply-timeout.
     */
    @Value("${app.rabbitmq.template.reply-timeout:30000}")
    private long rabbitReplyTimeoutMs;

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        rabbitTemplate.setReplyTimeout(rabbitReplyTimeoutMs);
        return rabbitTemplate;
    }

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        admin.setAutoStartup(true);
        return admin;
    }

    @Bean
    public Declarables orderModuleDeclarables() {
        return new Declarables(
                orderExchange(),
                orderCreateRequestQueue(),
                orderCreateRequestBinding(),
                orderStatusHistoryNotesLookupRequestQueue(),
                orderStatusHistoryNotesLookupRequestBinding()
        );
    }

    @Bean
    public Declarables authPasswordRpcDeclarables() {
        return new Declarables(
                authExchange(),
                userCreateRpcQueue(),
                userCreateRpcBinding(),
                authVerifyPasswordQueue(),
                authUpdatePasswordQueue(),
                userUpdateAccountLockedRpcQueue(),
                authVerifyPasswordBinding(),
                authUpdatePasswordBinding(),
                userUpdateAccountLockedRpcBinding()
        );
    }

    @Bean
    public Declarables shipmentPickupRequestedDeclarables() {
        return new Declarables(
                shipmentPickupRequestedOrderQueue(),
                shipmentPickupRequestedOrderBinding(),
                shipmentPickupRequestedDeliveryQueue(),
                shipmentPickupRequestedDeliveryBinding()
        );
    }

    @Bean
    public Declarables inventoryLookupDeclarables() {
        return new Declarables(
                inventoryExchange(),
                productExchange(),
                stockCheckRequestQueue(),
                stockCheckRequestBinding(),
                inventoryProductLookupRequestQueue(),
                inventoryProductLookupRequestBinding(),
                inventoryProductBulkLookupRequestQueue(),
                inventoryProductBulkLookupRequestBinding()
        );
    }

    /**
     * Explicitly declare POS cross-module lookup bindings on every startup.
     * Prevents silent broker state loss where queues exist but bindings are missing.
     */
    @Bean
    public Binding orderCreateRequestBinding() {
        return BindingBuilder
                .bind(orderCreateRequestQueue())
                .to(orderExchange())
                .with("order.create.request");
    }

    @Bean
    public Binding orderStatusHistoryNotesLookupRequestBinding() {
        return BindingBuilder
                .bind(orderStatusHistoryNotesLookupRequestQueue())
                .to(orderExchange())
                .with("order.status.history.notes.lookup.request");
    }

    @Bean
    public Declarables customerLookupDeclarables() {
        return new Declarables(
                customerExchange(),
                customerLookupRequestQueue(),
                customerLookupRequestBinding(),
                customerByUserIdRequestQueue(),
                customerByUserIdRequestBinding(),
                customerDetailsRequestQueue(),
                customerDetailsRequestBinding(),
                customerSearchRequestQueue(),
                customerSearchRequestBinding(),
                customerSimpleListRequestQueue(),
                customerUpdateRequestQueue(),
                customerUpdateRequestBinding()
        );
    }

    @Bean
    public Declarables authEmailLookupDeclarables() {
        return new Declarables(
                authExchange(),
                userEmailLookupRequestQueue(),
                userEmailLookupRequestBinding()
        );
    }

    @Bean
    public Declarables posLookupDeclarables() {
        return new Declarables(
                posVariantLookupRequestQueue(),
                posVariantLookupRequestBinding(),
                posCustomerLookupRequestQueue(),
                posCustomerLookupRequestBinding(),
                posProductListRequestQueue(),
                posProductListRequestBinding(),
                posCashierLookupRequestQueue(),
                posCashierLookupRequestBinding()
        );
    }

    @Bean
    public Declarables resellerLookupDeclarables() {
        return new Declarables(
                resellerDetailsLookupRequestQueue(),
                resellerDetailsLookupRequestBinding(),
                resellerProductDetailLookupRequestQueue(),
                resellerProductDetailLookupRequestBinding()
        );
    }

    @Bean
    public Binding posVariantLookupRequestBinding() {
        return BindingBuilder
                .bind(posVariantLookupRequestQueue())
                .to(productExchange())
                .with("pos.variant.lookup.request");
    }

    @Bean
    public Binding posCustomerLookupRequestBinding() {
        return BindingBuilder
                .bind(posCustomerLookupRequestQueue())
                .to(customerExchange())
                .with("pos.customer.lookup.request");
    }

    @Bean
    public Binding posProductListRequestBinding() {
        return BindingBuilder
                .bind(posProductListRequestQueue())
                .to(productExchange())
                .with("pos.product.list.request");
    }

    @Bean
    public Queue resellerCourierLookupRequestQueue() {
        return QueueBuilder.durable(resellerCourierLookupRequestQueue).build();
    }

    @Bean
    public Binding resellerCourierLookupRequestBinding() {
        return BindingBuilder
                .bind(resellerCourierLookupRequestQueue())
                .to(deliveryExchange())
                .with("reseller.courier.lookup.request");
    }

    @Bean
    public Queue resellerCartItemsLookupRequestQueue() {
        return QueueBuilder.durable(resellerCartItemsLookupRequestQueue).build();
    }

    @Bean
    public Queue resellerOrdersListLookupRequestQueue() {
        return QueueBuilder.durable(resellerOrdersListLookupRequestQueue).build();
    }


    // ── Reseller Details Lookup Queue ───────────────────────────────────────

    @Bean
    public Queue resellerOrderDeliveredQueue() {
        return QueueBuilder.durable(resellerOrderDeliveredQueue).build();
    }
    @Bean
    public Queue resellerDetailsLookupRequestQueue() {
        return QueueBuilder.durable(resellerDetailsLookupRequestQueue).build();
    }

    @Bean
    public Binding resellerDetailsLookupRequestBinding() {
        return BindingBuilder
                .bind(resellerDetailsLookupRequestQueue())
                .to(resellerExchange())
                .with("reseller.details.lookup.request");
    }

    // ── Reseller Name Lookup Queue ──────────────────────────────────────────

//    @Bean
//    public Queue resellerNameLookupRequestQueue() {
//        return QueueBuilder.durable(resellerNameLookupRequestQueue).build();
//    }
//
//    @Bean
//    public Binding resellerNameLookupRequestBinding() {
//        return BindingBuilder
//                .bind(resellerNameLookupRequestQueue())
//                .to(resellerExchange())
//                .with("reseller.name.lookup.request");
//    }

    // ── Reseller Product Detail Lookup Queue (NEW) ──────────────────────────

    @Bean
    public Queue resellerProductDetailLookupRequestQueue() {
        return QueueBuilder.durable(resellerProductDetailLookupRequestQueue).build();
    }

    @Bean
    public Binding resellerProductDetailLookupRequestBinding() {
        return BindingBuilder
                .bind(resellerProductDetailLookupRequestQueue())
                .to(productExchange())
                .with("reseller.product.detail.lookup.request");
    }

    // ── Admin Details Lookup Queue ──────────────────────────────────────────

    @Bean
    public Queue adminDetailsLookupRequestQueue() {
        return QueueBuilder.durable(adminDetailsLookupRequestQueue).build();
    }

    @Bean
    public Binding adminDetailsLookupRequestBinding() {
        return BindingBuilder
                .bind(adminDetailsLookupRequestQueue())
                .to(adminExchange())
                .with("admin.details.lookup.request");
    }

    // ── Admin Role Permission Lookup Queue ─────────────────────────────────

    @Bean
    public Queue adminRolePermissionLookupRequestQueue() {
        return QueueBuilder.durable(adminRolePermissionLookupRequestQueue).build();
    }

    @Bean
    public Binding adminRolePermissionLookupRequestBinding() {
        return BindingBuilder
                .bind(adminRolePermissionLookupRequestQueue())
                .to(adminExchange())
                .with("admin.role.permission.lookup.request");
    }

    // ── POS Cashier Lookup Queue ────────────────────────────────────────────

    @Bean
    public Queue posCashierLookupRequestQueue() {
        return QueueBuilder.durable(posCashierLookupRequestQueue).build();
    }

    @Bean
    public Binding posCashierLookupRequestBinding() {
        return BindingBuilder
                .bind(posCashierLookupRequestQueue())
                .to(posExchange())
                .with("pos.cashier.lookup.request");
    }

    @Bean
    public Queue posCashierByAdminLookupRequestQueue() {
        return QueueBuilder.durable(posCashierByAdminLookupRequestQueue).build();
    }

    @Bean
    public Binding posCashierByAdminLookupRequestBinding() {
        return BindingBuilder
                .bind(posCashierByAdminLookupRequestQueue())
                .to(posExchange())
                .with("pos.cashier.by.admin.lookup.request");
    }

    // ── RESELLER DASHBOARD & WALLET SUMMARY LOOKUP QUEUES (RPC) ──────────────

    /**
     * Reseller Dashboard Metrics Lookup Queue
     * RPC request from Reseller module to Order module
     * Request: { "requestType": "GET_RESELLER_DASHBOARD_METRICS", "resellerId": Long }
     * Response: { "success": boolean, "totalSales": BigDecimal, "totalOrders": Long,
     *             "pendingOrders": Long, "recentOrders": List<Map> }
     */
    @Bean
    public Queue resellerDashboardMetricsLookupRequestQueue() {
        return QueueBuilder.durable(resellerDashboardMetricsLookupRequestQueue).build();
    }

    @Bean
    public Binding resellerDashboardMetricsLookupRequestBinding() {
        return BindingBuilder
                .bind(resellerDashboardMetricsLookupRequestQueue())
                .to(orderExchange())
                .with("reseller.dashboard.metrics.lookup.request");
    }

    /**
     * Customer Order Counts Lookup Queue
     * RPC request from Customer module to Order module.
     */
    @Bean
    public Queue customerOrderCountsLookupRequestQueue() {
        return QueueBuilder.durable(customerOrderCountsLookupRequestQueue).build();
    }

    @Bean
    public Binding customerOrderCountsLookupRequestBinding() {
        return BindingBuilder
                .bind(customerOrderCountsLookupRequestQueue())
                .to(orderExchange())
                .with("customer.order.counts.lookup.request");
    }

    /**
     * Reseller Wallet Summary Lookup Queue
     * RPC request from Reseller module to Payment module
     * Request: { "requestType": "GET_RESELLER_WALLET_SUMMARY", "resellerId": Long }
     * Response: { "success": boolean, "totalEarnings": BigDecimal, "totalWithdrawn": BigDecimal,
     *             "recentTransactions": List<Map> }
     */
    @Bean
    public Queue resellerWalletSummaryLookupRequestQueue() {
        return QueueBuilder.durable(resellerWalletSummaryLookupRequestQueue).build();
    }

    @Bean
    public Binding resellerWalletSummaryLookupRequestBinding() {
        return BindingBuilder
                .bind(resellerWalletSummaryLookupRequestQueue())
                .to(paymentExchange())
                .with("reseller.wallet.summary.lookup.request");
    }

    /**
     * Reseller Dashboard & Wallet Declarables
     * Groups all reseller dashboard and wallet RPC queues for explicit declaration on startup
     */
    @Bean
    public Declarables resellerDashboardWalletDeclarables() {
        return new Declarables(
                resellerDashboardMetricsLookupRequestQueue(),
                resellerDashboardMetricsLookupRequestBinding(),
                resellerWalletSummaryLookupRequestQueue(),
                resellerWalletSummaryLookupRequestBinding()
        );
    }

    @Bean
    public Declarables adminEventDeclarables() {
        return new Declarables(
                adminExchange(),
                adminUserCreateQueue(),
                adminUserCreateBinding(),
                adminUserRoleUpdateQueue(),
                adminUserRoleUpdateBinding(),
                adminPermissionsInitializeQueue(),
                adminPermissionsInitializeBinding(),
                adminRoleCreateQueue(),
                adminRoleCreateBinding(),
                adminPermissionsAssignQueue(),
                adminPermissionsAssignBinding(),
                adminUserDeleteQueue(),
                adminUserDeleteBinding(),
                adminUserPasswordUpdateQueue(),
                adminUserPasswordUpdateBinding(),
                adminUserDetailsUpdateQueue(),
                adminUserDetailsUpdateBinding()
        );
    }

}
