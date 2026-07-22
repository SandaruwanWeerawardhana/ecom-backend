package org.psint.beyosclothing.modules.customers.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.shared.dto.CustomerLookupRequest;
import org.psint.beyosclothing.shared.dto.CustomerLookupResponse;
import org.psint.beyosclothing.modules.customers.entity.Customer;
import org.psint.beyosclothing.modules.customers.repository.CustomerRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * Consumer for customer lookup requests from Order module and Auth module
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CustomerLookupConsumer {

    private final CustomerRepository customerRepository;

    @RabbitListener(queues = "${app.rabbitmq.queue.customer-lookup-request}")
    @Transactional(value = "customerTransactionManager", readOnly = true)
    public CustomerLookupResponse handleCustomerLookupRequest(CustomerLookupRequest request) {
        log.info("Received customer lookup request - Request ID: {}, Customer UUID: {}",
                request.getRequestId(), request.getCustomerUuid());

        try {
            Customer customer = customerRepository.findByUuid(request.getCustomerUuid())
                    .orElse(null);

            if (customer == null) {
                log.warn("Customer not found - UUID: {}", request.getCustomerUuid());
                return CustomerLookupResponse.builder()
                        .requestId(request.getRequestId())
                        .found(false)
                        .build();
            }

            log.info("Customer found - ID: {}, UUID: {}", customer.getId(), customer.getUuid());

            return CustomerLookupResponse.builder()
                    .requestId(request.getRequestId())
                    .found(true)
                    .customerId(customer.getId())
                    .customerUuid(customer.getUuid())
                    .firstName(customer.getFirstName())
                    .lastName(customer.getLastName())
                    .phoneNumber(customer.getPhoneNumber())
                    .customerType("CUSTOMER") // TODO: Implement reseller logic
                    .build();

        } catch (Exception e) {
            log.error("Error looking up customer - Request ID: {}", request.getRequestId(), e);
            return CustomerLookupResponse.builder()
                    .requestId(request.getRequestId())
                    .found(false)
                    .build();
        }
    }

    /**
     * Consumer for customer lookup by User ID (for Auth module)
     * Returns customer details INCLUDING EMAIL fetched from Auth module via RabbitMQ
     * Flow:
     * 1. Query Customer table for customer details
     * 2. Fetch email from User table (Auth DB) via RabbitMQ RPC
     * 3. Return combined response
     */
    @RabbitListener(queues = "${app.rabbitmq.queue.customer-by-userid-request}")
    @Transactional(value = "customerTransactionManager", readOnly = true)
    public Map<String, Object> handleCustomerByUserIdRequest(Map<String, Object> request) {
        Long userId = ((Number) request.get("userId")).longValue();
        String requestId = (String) request.get("requestId");

        log.info("📥 Received customer lookup by userId request - Request ID: {}, User ID: {}",
                requestId, userId);

        Map<String, Object> response = new HashMap<>();
        response.put("requestId", requestId);

        try {
            // Step 1: Fetch customer details from Customer table
            log.debug("Querying customers table for userId: {}", userId);
            Customer customer = customerRepository.findByUserId(userId).orElse(null);

            if (customer == null) {
                log.warn("⚠️ Customer not found in customers table for userId: {}", userId);
                response.put("found", false);
                return response;
            }

            log.info("✅ Customer found in customers table - Customer ID: {}, UUID: {}, User ID: {}",
                    customer.getId(), customer.getUuid(), userId);



            // Step 3: Build response with email
            response.put("found", true);
            response.put("customerId", customer.getId());
            response.put("customerUuid", customer.getUuid());
            response.put("firstName", customer.getFirstName());
            response.put("lastName", customer.getLastName());
            response.put("phoneNumber", customer.getPhoneNumber());
            response.put("email", customer.getEmail()); // ✅ Email from Auth module

            log.info("📤 Sending customer lookup response - requestId: {}, found: true, customerId: {}, email: {}",
                    requestId, customer.getId(), customer.getEmail());

        } catch (Exception e) {
            log.error("❌ Error looking up customer by userId - Request ID: {}, User ID: {}, Error: {}",
                    requestId, userId, e.getMessage(), e);
            response.put("found", false);
            response.put("error", e.getMessage());
        }

        return response;
    }
}
