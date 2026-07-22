package org.psint.beyosclothing.modules.pos.repository;

import org.psint.beyosclothing.modules.pos.entity.PosCashierEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * POS Cashier Repository
 * Manages CRUD operations for POS cashiers
 */
@Repository
public interface PosCashierRepository extends JpaRepository<PosCashierEntity, Long> {

    /**
     * Find cashier by UUID
     */
    Optional<PosCashierEntity> findByUuid(String uuid);

    /**
     * Find cashier by user ID (linked admin user)
     */
    Optional<PosCashierEntity> findByUserId(Long userId);

    /**
     * Find all active cashiers
     */
    List<PosCashierEntity> findByIsActiveTrue();

    /**
     * Find active cashier by user ID (for RabbitMQ request-reply pattern)
     * Returns first active cashier if multiple exist
     */
    Optional<PosCashierEntity> findByUserIdAndIsActiveTrue(Long userId);
}

