package org.psint.beyosclothing.modules.pos.service;

import org.psint.beyosclothing.modules.pos.dto.request.CreatePosCustomerRequest;
import org.psint.beyosclothing.modules.pos.dto.request.UpdatePosCustomerRequest;
import org.psint.beyosclothing.modules.pos.dto.response.PosCustomerDetailsResponse;
import org.psint.beyosclothing.modules.pos.dto.response.PosCustomerSearchResponse;
import org.psint.beyosclothing.modules.pos.dto.response.PosCustomerSimpleResponse;

import java.util.List;

/**
 * POS Customer Service
 * Handles customer lookup operations for POS system with caching and fault tolerance
 */
public interface PosCustomerService {

    /**
     * Search customers by name, phone, or email for POS autocomplete
     * Results are cached in Redis for fast repeated lookups
     * Optimized for < 100ms response time
     *
     * @param query Search term (name, phone, or email) - minimum 2 characters
     * @param limit Maximum number of results to return (default 10, max 50)
     * @return List of matching customers ordered by recent activity
     */
    List<PosCustomerSearchResponse> searchCustomers(String query, int limit);

    /**
     * Get full customer details by customer ID
     * Results are cached in Redis for fast repeated lookups
     *
     * @param customerId Customer ID
     * @return Customer details with addresses
     */
    PosCustomerDetailsResponse getCustomerDetails(Long customerId);

    /**
     * Create a POS customer (for walk-in or quick checkout)
     *
     * @param request Customer information for creation
     * @return Details of the created customer
     */
    PosCustomerDetailsResponse createPosCustomer(CreatePosCustomerRequest request);

    /**
     * Update an existing POS customer by ID
     *
     * @param customerId ID of the customer to update
     * @param request    Updated customer information
     * @return Updated customer details
     */
    PosCustomerDetailsResponse updatePosCustomer(Long customerId, UpdatePosCustomerRequest request);

    /**
     * Return all POS customers with minimal fields for listing (uuid + fullName)
     * The returned objects include a `source` field set to "POS" so callers can mark origin
     */
    List<PosCustomerSimpleResponse> getAllSimpleCustomers();
}
