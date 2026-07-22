package org.psint.beyosclothing.modules.pos.repository;

import org.psint.beyosclothing.modules.pos.entity.PosStockSyncEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * POS Stock Sync Repository
 * Manages audit trail for inventory deductions
 */
@Repository
public interface PosStockSyncRepository extends JpaRepository<PosStockSyncEntity, Long> {

    /**
     * Find by UUID
     */
    Optional<PosStockSyncEntity> findByUuid(String uuid);

    /**
     * Find all sync records for a POS order
     */
    List<PosStockSyncEntity> findByPosOrderId(Long posOrderId);

    /**
     * Find all unsynced records
     */
    List<PosStockSyncEntity> findBySynced(Boolean synced);

    /**
     * Find unsynced records older than specified time for retry
     */
    @Query("SELECT s FROM PosStockSyncEntity s WHERE s.synced = false " +
           "AND s.createdAt < :cutoffTime")
    List<PosStockSyncEntity> findUnsyncedOlderThan(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Count unsynced records for monitoring
     */
    long countBySynced(Boolean synced);

    /**
     * Find by product ID
     */
    List<PosStockSyncEntity> findByProductId(Long productId);
}
