package org.psint.beyosclothing.common.service;

import java.util.Optional;

/**
 * Customer Query Service Interface (API Contract)
 * This interface defines the contract for customer-related queries
 * In microservice architecture:
 * - Customer Service will implement this interface
 * - Other services will call this via HTTP client (Feign/RestTemplate)
 * - This interface becomes the REST API contract
 */
public interface CustomerQueryService {

    /**
     * Get customer ID by user email
     * Used for authentication context where we have email from JWT
     *
     * @param email User email from authentication
     * @return Customer ID if found
     */
    Optional<Long> getCustomerIdByEmail(String email);

    /**
     * Get customer ID by user ID
     * Used when we have user ID from auth context
     *
     * @param userId User ID from auth database
     * @return Customer ID if found
     */
    Optional<Long> getCustomerIdByUserId(Long userId);

    /**
     * Check if customer exists for given email
     *
     * @param email User email
     * @return true if customer profile exists
     */
    boolean customerExistsByEmail(String email);

    /**
     * Check if customer exists for given user ID
     *
     * @param userId User ID from auth database
     * @return true if customer profile exists
     */
    boolean customerExistsByUserId(Long userId);
}

