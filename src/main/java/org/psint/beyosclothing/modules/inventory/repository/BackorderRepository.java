package org.psint.beyosclothing.modules.inventory.repository;

import org.psint.beyosclothing.modules.inventory.entity.BackOrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Backorder Repository
 * Database: beyos_inventory_db
 */
@Repository
public interface BackorderRepository extends JpaRepository<BackOrderEntity, Long> {

    List<BackOrderEntity> findByProductId(Long productId);

    List<BackOrderEntity> findByOrderId(Long orderId);

    List<BackOrderEntity> findByFulfilledFalseAndIsActiveTrue();
}
