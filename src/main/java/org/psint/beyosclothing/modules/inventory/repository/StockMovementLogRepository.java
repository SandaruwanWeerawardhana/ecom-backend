package org.psint.beyosclothing.modules.inventory.repository;

import org.psint.beyosclothing.modules.inventory.entity.StockMovementLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Stock Movement Log Repository
 * Database: beyos_inventory_db
 */
@Repository
public interface StockMovementLogRepository extends JpaRepository<StockMovementLogEntity, Long> {

    List<StockMovementLogEntity> findByProductIdOrderByDateCreatedDesc(Long productId);

    List<StockMovementLogEntity> findByProductIdAndVariantIdOrderByDateCreatedDesc(Long productId, Long variantId);

    List<StockMovementLogEntity> findByReferenceTypeAndReferenceId(String referenceType, Long referenceId);
}
