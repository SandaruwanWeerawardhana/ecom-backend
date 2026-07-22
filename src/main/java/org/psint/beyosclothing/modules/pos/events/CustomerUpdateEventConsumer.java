package org.psint.beyosclothing.modules.pos.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Consumer for customer update events
 * Invalidates POS customer cache when customer data changes
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CustomerUpdateEventConsumer {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Handle customer updated event
     * Clears cached customer details and related search results
     */
    @RabbitListener(queues = "${app.rabbitmq.queue.customer-updated:customer.updated.queue}")
    public void handleCustomerUpdated(CustomerUpdateEvent event) {
        try {
            log.info("Received customer update event - customerId: {}, uuid: {}", event.getCustomerId(), event.getCustomerUuid());

            invalidateCustomerCache(event.getCustomerId(), event.getCustomerUuid());

        } catch (Exception e) {
            log.error("Failed to process customer update event: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle customer deleted event
     * Clears all cached data for the customer
     */
    @RabbitListener(queues = "${app.rabbitmq.queue.customer-deleted:customer.deleted.queue}")
    public void handleCustomerDeleted(CustomerUpdateEvent event) {
        try {
            log.info("Received customer deleted event - customerId: {}", event.getCustomerId());

            invalidateCustomerCache(event.getCustomerId(), event.getCustomerUuid());

        } catch (Exception e) {
            log.error("Failed to process customer deleted event: {}", e.getMessage(), e);
        }
    }

    /**
     * Invalidate customer cache
     * Removes customer details cache and all related search caches
     */
    private void invalidateCustomerCache(Long customerId, String customerUuid) {
        if (customerId == null && customerUuid == null) {
            log.warn("Cannot invalidate cache - both customerId and customerUuid are null");
            return;
        }

        try {
            int invalidatedKeys = 0;

            // 1. Invalidate customer details cache by ID
            if (customerId != null) {
                String detailsKey = "pos:customer:" + customerId;
                Boolean deleted = redisTemplate.delete(detailsKey);
                if (Boolean.TRUE.equals(deleted)) {
                    invalidatedKeys++;
                    log.debug("Invalidated customer details cache: {}", detailsKey);
                }
            }

            // 2. Invalidate all search caches (pattern-based deletion)
            // This clears all search results that might contain the updated customer
            invalidateSearchCaches();
            invalidatedKeys += clearSearchPattern();

            log.info("Cache invalidation completed - customerId: {}, keys invalidated: {}", customerId, invalidatedKeys);

        } catch (Exception e) {
            log.error("Failed to invalidate customer cache for customerId {}: {}", customerId, e.getMessage());
            // Service continues even if cache invalidation fails (graceful degradation)
        }
    }

    /**
     * Invalidate all customer search caches
     * Uses pattern-based deletion to clear all search result caches
     */
    private void invalidateSearchCaches() {
        try {
            Set<String> searchKeys = redisTemplate.keys("pos:customer:search:*");

            if (searchKeys != null && !searchKeys.isEmpty()) {
                Long deletedCount = redisTemplate.delete(searchKeys);
                log.info("Cleared {} search cache entries", deletedCount != null ? deletedCount : 0);
            } else {
                log.debug("No search cache entries to clear");
            }

        } catch (Exception e) {
            log.warn("Failed to clear search caches: {}", e.getMessage());
        }
    }

    /**
     * Clear search pattern caches
     * Returns count of cleared keys
     */
    private int clearSearchPattern() {
        try {
            Set<String> keys = redisTemplate.keys("pos:customer:search:*");
            if (keys != null && !keys.isEmpty()) {
                Long deleted = redisTemplate.delete(keys);
                return deleted != null ? deleted.intValue() : 0;
            }
            return 0;
        } catch (Exception e) {
            log.warn("Failed to clear search pattern: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Customer update event DTO
     */
    public static class CustomerUpdateEvent {
        private Long customerId;
        private String customerUuid;
        private String action; // UPDATED, DELETED

        public CustomerUpdateEvent() {}

        public CustomerUpdateEvent(Long customerId, String customerUuid, String action) {
            this.customerId = customerId;
            this.customerUuid = customerUuid;
            this.action = action;
        }

        public Long getCustomerId() {
            return customerId;
        }

        public void setCustomerId(Long customerId) {
            this.customerId = customerId;
        }

        public String getCustomerUuid() {
            return customerUuid;
        }

        public void setCustomerUuid(String customerUuid) {
            this.customerUuid = customerUuid;
        }

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }
    }
}
