package org.psint.beyosclothing.modules.customers.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.common.service.CustomerQueryService;
import org.psint.beyosclothing.modules.auth.entity.User;
import org.psint.beyosclothing.modules.auth.repository.UserRepository;
import org.psint.beyosclothing.modules.customers.entity.Customer;
import org.psint.beyosclothing.modules.customers.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Customer Query Service Implementation
 * Handles cross-module queries for customer data
 * In microservice architecture, this will be exposed as REST endpoints
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerQueryServiceImpl implements CustomerQueryService {

    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(value = "customerTransactionManager", readOnly = true)
    public Optional<Long> getCustomerIdByEmail(String email) {
        log.debug("Getting customer ID for email: {}", email);

        // First, get user from auth database
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            log.debug("User not found with email: {}", email);
            return Optional.empty();
        }

        User user = userOpt.get();

        // Verify user is a customer
        if (!"CUSTOMER".equals(user.getUserType())) {
            log.warn("User {} is not a customer. User type: {}", email, user.getUserType());
            return Optional.empty();
        }

        // Then get customer from customer database using userId
        Optional<Customer> customerOpt = customerRepository.findByUserId(user.getId());
        if (customerOpt.isEmpty()) {
            log.warn("Customer profile not found for user ID: {}", user.getId());
            return Optional.empty();
        }

        log.debug("Found customer ID: {} for email: {}", customerOpt.get().getId(), email);
        return Optional.of(customerOpt.get().getId());
    }

    @Override
    @Transactional(value = "customerTransactionManager", readOnly = true)
    public Optional<Long> getCustomerIdByUserId(Long userId) {
        log.debug("Getting customer ID for user ID: {}", userId);

        Optional<Customer> customerOpt = customerRepository.findByUserId(userId);
        if (customerOpt.isEmpty()) {
            log.debug("Customer not found for user ID: {}", userId);
            return Optional.empty();
        }

        log.debug("Found customer ID: {} for user ID: {}", customerOpt.get().getId(), userId);
        return Optional.of(customerOpt.get().getId());
    }

    @Override
    @Transactional(value = "customerTransactionManager", readOnly = true)
    public boolean customerExistsByEmail(String email) {
        log.debug("Checking if customer exists for email: {}", email);

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty() || !"CUSTOMER".equals(userOpt.get().getUserType())) {
            return false;
        }

        return customerRepository.existsByUserId(userOpt.get().getId());
    }

    @Override
    @Transactional(value = "customerTransactionManager", readOnly = true)
    public boolean customerExistsByUserId(Long userId) {
        log.debug("Checking if customer exists for user ID: {}", userId);
        return customerRepository.existsByUserId(userId);
    }
}

