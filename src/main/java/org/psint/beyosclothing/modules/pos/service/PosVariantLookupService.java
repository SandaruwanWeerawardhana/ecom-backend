package org.psint.beyosclothing.modules.pos.service;

/**
 * POS Variant Lookup Service
 * Handles variant UUID to ID conversion via RabbitMQ
 * Decouples POS module from Product module
 */
public interface PosVariantLookupService {

    /**
     * Convert variant UUID to variant ID via RabbitMQ
     * @param variantUuid the variant UUID
     * @return variant ID (Long) or null if not found
     */
    Long getVariantIdByUuid(String variantUuid);

    /**
     * Convert variant ID to variant UUID via RabbitMQ
     * @param variantId the variant ID
     * @return variant UUID (String) or null if not found
     */
    String getVariantUuidById(Long variantId);
}

