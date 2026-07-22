package org.psint.beyosclothing.modules.customers.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.auth.events.UserCreatedEvent;
import org.psint.beyosclothing.modules.customers.entity.Customer;
import org.psint.beyosclothing.modules.customers.repository.CustomerRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Auth Event Consumer in Customer Module
 * Listens to UserCreatedEvent from Auth module and creates customer records
 * This ensures when a CUSTOMER/RESELLER registers, their profile is created in Customer DB
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuthEventConsumer {

    private final CustomerRepository customerRepository;

    /**
     * Listen to user creation events
     * When a user with type CUSTOMER or RESELLER is created in Auth DB,
     * create corresponding customer record in Customer DB
     */
    @RabbitListener(queues = "customer.created.queue")
    @Transactional("customerTransactionManager")
    public void handleUserCreated(UserCreatedEvent event) {
        log.info("Received UserCreatedEvent for userId: {}, userType: {}", event.getUserId(), event.getUserType());

        try {
            // Only process CUSTOMER and RESELLER user types
            if (!"Customer".equals(event.getUserType()) && !"Reseller".equals(event.getUserType())) {
                log.debug("Skipping customer creation for userType: {}", event.getUserType());
                return;
            }

            // Check if customer already exists
            if (customerRepository.existsByUserId(event.getUserId())) {
                log.warn("Customer already exists for userId: {}", event.getUserId());
                return;
            }

            // Create customer entity
            Customer customer = Customer.builder()
                    .userId(event.getUserId())
                    .firstName(event.getFirstName())
                    .lastName(event.getLastName())
                    .email(event.getEmail())
                    .phoneNumber(event.getPhone())
                    .build();

            customer = customerRepository.save(customer);
            log.info("✅ Customer created successfully - ID: {}, UserId: {}, Email: {}",
                    customer.getId(), customer.getUserId(), customer.getEmail());

        } catch (Exception e) {
            log.error("❌ Error creating customer for userId {}: {}", event.getUserId(), e.getMessage(), e);
            // Don't throw exception - we don't want to block the user registration
            // The customer record can be created later via reconciliation job
        }
    }

    private String buildFullName(String firstName, String lastName) {
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        } else if (firstName != null) {
            return firstName;
        } else if (lastName != null) {
            return lastName;
        }
        return null;
    }
}
