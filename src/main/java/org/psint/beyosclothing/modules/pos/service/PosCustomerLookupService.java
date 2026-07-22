package org.psint.beyosclothing.modules.pos.service;

/**
 * POS Customer Lookup Service
 * Handles customer UUID to ID conversion via RabbitMQ
 * Decouples POS module from Customer module
 */
public interface PosCustomerLookupService {

    /**
     * Convert customer UUID to customer ID via RabbitMQ
     * @param customerUuid the customer UUID
     * @return customer ID (Long) or null if not found
     */
    Long getCustomerIdByUuid(String customerUuid);

    /**
     * Convert customer ID to customer UUID via RabbitMQ
     * @param customerId the customer ID
     * @return customer UUID (String) or null if not found
     */
    String getCustomerUuidById(Long customerId);
}

