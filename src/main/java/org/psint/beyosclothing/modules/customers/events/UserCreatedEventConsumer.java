package org.psint.beyosclothing.modules.customers.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.auth.events.UserCreatedEvent;
import org.psint.beyosclothing.modules.customers.entity.Customer;
import org.psint.beyosclothing.modules.customers.repository.CustomerRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * User Created Event Consumer
 * Listens to user.created events from Auth module
 * Creates customer profile in customer database
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserCreatedEventConsumer {

    private final CustomerRepository customerRepository;

    @RabbitListener(queues = "${app.rabbitmq.queue.customer-created}")
    @Transactional("customerTransactionManager")
    public void handleUserCreatedEvent(UserCreatedEvent event) {
        log.info("Received UserCreatedEvent for userId: {}, role: {}", event.getUserId(), event.getRole());

        try {
            // Only create customer profile for CUSTOMER and RESELLER roles
            if ("Customer".equals(event.getRole()) || "Reseller".equals(event.getRole())) {

                // Check if customer already exists (idempotent consumer)
                if (customerRepository.existsByUserId(event.getUserId())) {
                    log.warn("Customer already exists for userId: {}. Skipping creation.", event.getUserId());
                    return;
                }

                // Create customer profile with UUID auto-generated
                Customer customer = Customer.builder()
                        .uuid(java.util.UUID.randomUUID().toString()) // Generate UUID
                        .userId(event.getUserId())
                        .firstName(event.getFirstName())
                        .lastName(event.getLastName())
                        .phoneNumber(event.getPhone())
                        .build();

                customerRepository.save(customer);
                log.info("Customer profile created successfully - UUID: {}, userId: {}, Name: {} {}",
                        customer.getUuid(), event.getUserId(), event.getFirstName(), event.getLastName());
            } else {
                log.debug("User role {} does not require customer profile creation", event.getRole());
            }
        } catch (Exception e) {
            log.error("Failed to process UserCreatedEvent for userId: {}", event.getUserId(), e);
            // In production, this should go to a dead-letter queue for retry
            throw new RuntimeException("Failed to create customer profile", e);
        }
    }
}
