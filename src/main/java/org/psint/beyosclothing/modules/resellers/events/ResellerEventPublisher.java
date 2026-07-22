package org.psint.beyosclothing.modules.resellers.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Service for publishing reseller-related events to RabbitMQ
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ResellerEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.rabbitmq.exchange.reseller}")
    private String resellerExchange;

    @Value("${app.rabbitmq.routing-key.reseller-registered}")
    private String resellerRegisteredRoutingKey;

    @Value("${app.rabbitmq.routing-key.reseller-approved}")
    private String resellerApprovedRoutingKey;

    @Value("${app.rabbitmq.routing-key.reseller-rejected}")
    private String resellerRejectedRoutingKey;

    @Value("${app.rabbitmq.routing-key.reseller-suspended}")
    private String resellerSuspendedRoutingKey;

    @Value("${app.rabbitmq.routing-key.reseller-order-placed}")
    private String resellerOrderPlacedRoutingKey;

    @Value("${app.rabbitmq.routing-key.reseller-wallet-credited}")
    private String resellerWalletCreditedRoutingKey;

    @Value("${app.rabbitmq.routing-key.reseller-withdrawal-requested}")
    private String resellerWithdrawalRequestedRoutingKey;

    @Value("${app.rabbitmq.routing-key.reseller-withdrawal-processed}")
    private String resellerWithdrawalProcessedRoutingKey;

    /**
     * Publish reseller registered event
     */
    public void publishResellerRegistered(ResellerRegisteredEvent event) {
        try {
            event.setTimestamp(LocalDateTime.now());
            log.info("Publishing ResellerRegisteredEvent for reseller UUID: {}", event.getResellerUuid());
            rabbitTemplate.convertAndSend(resellerExchange, resellerRegisteredRoutingKey, event);
            log.debug("ResellerRegisteredEvent published successfully");
        } catch (Exception e) {
            log.error("Failed to publish ResellerRegisteredEvent for reseller UUID: {}", event.getResellerUuid(), e);
        }
    }

    /**
     * Publish reseller approved event
     */
    public void publishResellerApproved(ResellerApprovedEvent event) {
        try {
            event.setTimestamp(LocalDateTime.now());
            log.info("Publishing ResellerApprovedEvent for reseller UUID: {}", event.getResellerUuid());
            rabbitTemplate.convertAndSend(resellerExchange, resellerApprovedRoutingKey, event);
            log.debug("ResellerApprovedEvent published successfully");
        } catch (Exception e) {
            log.error("Failed to publish ResellerApprovedEvent for reseller UUID: {}", event.getResellerUuid(), e);
        }
    }

    /**
     * Publish reseller rejected event
     */
    public void publishResellerRejected(ResellerRejectedEvent event) {
        try {
            event.setTimestamp(LocalDateTime.now());
            log.info("Publishing ResellerRejectedEvent for reseller UUID: {}", event.getResellerUuid());
            rabbitTemplate.convertAndSend(resellerExchange, resellerRejectedRoutingKey, event);
            log.debug("ResellerRejectedEvent published successfully");
        } catch (Exception e) {
            log.error("Failed to publish ResellerRejectedEvent for reseller UUID: {}", event.getResellerUuid(), e);
        }
    }

    /**
     * Publish reseller suspended event
     */
    public void publishResellerSuspended(ResellerSuspendedEvent event) {
        try {
            event.setTimestamp(LocalDateTime.now());
            log.info("Publishing ResellerSuspendedEvent for reseller UUID: {}", event.getResellerUuid());
            rabbitTemplate.convertAndSend(resellerExchange, resellerSuspendedRoutingKey, event);
            log.debug("ResellerSuspendedEvent published successfully");
        } catch (Exception e) {
            log.error("Failed to publish ResellerSuspendedEvent for reseller UUID: {}", event.getResellerUuid(), e);
        }
    }

    /**
     * Publish reseller order placed event
     */
    public void publishResellerOrderPlaced(ResellerOrderPlacedEvent event) {
        try {
            event.setTimestamp(LocalDateTime.now());
            log.info("Publishing ResellerOrderPlacedEvent for order UUID: {}", event.getOrderUuid());
            rabbitTemplate.convertAndSend(resellerExchange, resellerOrderPlacedRoutingKey, event);
            log.debug("ResellerOrderPlacedEvent published successfully");
        } catch (Exception e) {
            log.error("Failed to publish ResellerOrderPlacedEvent for order UUID: {}", event.getOrderUuid(), e);
        }
    }

    /**
     * Publish wallet credited event
     */
    public void publishWalletCredited(ResellerWalletCreditedEvent event) {
        try {
            event.setTimestamp(LocalDateTime.now());
            log.info("Publishing ResellerWalletCreditedEvent for reseller UUID: {} - Amount: {}",
                    event.getResellerUuid(), event.getAmount());
            rabbitTemplate.convertAndSend(resellerExchange, resellerWalletCreditedRoutingKey, event);
            log.debug("ResellerWalletCreditedEvent published successfully");
        } catch (Exception e) {
            log.error("Failed to publish ResellerWalletCreditedEvent for reseller UUID: {}",
                    event.getResellerUuid(), e);
        }
    }

    /**
     * Publish wallet debited event
     */
    public void publishWalletDebited(ResellerWalletDebitedEvent event) {
        try {
            event.setTimestamp(LocalDateTime.now());
            log.info("Publishing ResellerWalletDebitedEvent for reseller UUID: {} - Amount: {}",
                    event.getResellerUuid(), event.getAmount());
            rabbitTemplate.convertAndSend(resellerExchange, resellerWalletCreditedRoutingKey, event);
            log.debug("ResellerWalletDebitedEvent published successfully");
        } catch (Exception e) {
            log.error("Failed to publish ResellerWalletDebitedEvent for reseller UUID: {}",
                    event.getResellerUuid(), e);
        }
    }

    /**
     * Publish withdrawal requested event
     */
    public void publishWithdrawalRequested(ResellerWithdrawalRequestedEvent event) {
        try {
            event.setTimestamp(LocalDateTime.now());
            log.info("Publishing ResellerWithdrawalRequestedEvent for withdrawal UUID: {}", event.getWithdrawalUuid());
            rabbitTemplate.convertAndSend(resellerExchange, resellerWithdrawalRequestedRoutingKey, event);
            log.debug("ResellerWithdrawalRequestedEvent published successfully");
        } catch (Exception e) {
            log.error("Failed to publish ResellerWithdrawalRequestedEvent for withdrawal UUID: {}",
                    event.getWithdrawalUuid(), e);
        }
    }

    /**
     * Publish withdrawal processed event
     */
    public void publishWithdrawalProcessed(ResellerWithdrawalProcessedEvent event) {
        try {
            event.setTimestamp(LocalDateTime.now());
            log.info("Publishing ResellerWithdrawalProcessedEvent for withdrawal UUID: {} - Status: {}",
                    event.getWithdrawalUuid(), event.getStatus());
            rabbitTemplate.convertAndSend(resellerExchange, resellerWithdrawalProcessedRoutingKey, event);
            log.debug("ResellerWithdrawalProcessedEvent published successfully");
        } catch (Exception e) {
            log.error("Failed to publish ResellerWithdrawalProcessedEvent for withdrawal UUID: {}",
                    event.getWithdrawalUuid(), e);
        }
    }
}

