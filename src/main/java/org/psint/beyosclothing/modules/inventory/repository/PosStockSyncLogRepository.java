package org.psint.beyosclothing.modules.inventory.repository;

import org.psint.beyosclothing.modules.inventory.entity.PosStockSyncLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * POS Stock Sync Log Repository
 * Database: beyos_inventory_db
 */
@Repository
public interface PosStockSyncLogRepository extends JpaRepository<PosStockSyncLogEntity, Long> {

    Optional<PosStockSyncLogEntity> findByUuid(String uuid);

    List<PosStockSyncLogEntity> findByProductIdOrderByDateCreatedDesc(Long productId);

    List<PosStockSyncLogEntity> findByProductIdAndVariantIdOrderByDateCreatedDesc(Long productId, Long variantId);

    List<PosStockSyncLogEntity> findBySyncStatusAndIsActiveTrue(PosStockSyncLogEntity.SyncStatus syncStatus);

    List<PosStockSyncLogEntity> findByPosTerminalOrderByDateCreatedDesc(String posTerminal);

    @Query("SELECT p FROM PosStockSyncLogEntity p WHERE p.syncStatus = 'FAILED' AND p.isActive = true ORDER BY p.dateCreated DESC")
    List<PosStockSyncLogEntity> findAllFailedSyncs();

    @Query("SELECT p FROM PosStockSyncLogEntity p WHERE p.dateCreated BETWEEN :startDate AND :endDate ORDER BY p.dateCreated DESC")
    List<PosStockSyncLogEntity> findSyncLogsBetweenDates(LocalDateTime startDate, LocalDateTime endDate);

    @Query("SELECT p FROM PosStockSyncLogEntity p WHERE p.posTerminal = :terminal AND p.dateCreated BETWEEN :startDate AND :endDate")
    List<PosStockSyncLogEntity> findByTerminalAndDateRange(String terminal, LocalDateTime startDate, LocalDateTime endDate);
}

