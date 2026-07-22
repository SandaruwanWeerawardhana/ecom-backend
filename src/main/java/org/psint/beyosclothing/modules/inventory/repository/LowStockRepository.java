package org.psint.beyosclothing.modules.inventory.repository;

import org.psint.beyosclothing.modules.inventory.entity.LowStockEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Low Stock Alerts Repository
 * Database: beyos_inventory_db
 */
@Repository
public interface LowStockRepository extends JpaRepository<LowStockEntity, Long> {

    Optional<LowStockEntity> findByUuid(String uuid);

    List<LowStockEntity> findByProductId(Long productId);

    List<LowStockEntity> findByProductIdAndVariantId(Long productId, Long variantId);

    List<LowStockEntity> findByAlertStatusAndIsActiveTrue(LowStockEntity.AlertStatus alertStatus);

    @Query("SELECT l FROM LowStockEntity l WHERE l.alertStatus = 'PENDING' AND l.notified = false AND l.isActive = true")
    List<LowStockEntity> findPendingUnnotifiedAlerts();

    @Query("SELECT l FROM LowStockEntity l WHERE l.alertStatus = 'PENDING' AND l.isActive = true ORDER BY l.dateCreated DESC")
    List<LowStockEntity> findAllPendingAlerts();

    @Query("SELECT l FROM LowStockEntity l WHERE l.alertStatus = 'ACKNOWLEDGED' AND l.isActive = true ORDER BY l.acknowledgedAt DESC")
    List<LowStockEntity> findAllAcknowledgedAlerts();

    boolean existsByProductIdAndVariantIdAndAlertStatus(Long productId, Long variantId, LowStockEntity.AlertStatus alertStatus);
}
