package org.psint.beyosclothing.modules.pos.repository;

import org.psint.beyosclothing.modules.pos.entity.PosReceiptEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Repository for POS Receipt operations
 */
@Repository
public interface PosReceiptRepository extends JpaRepository<PosReceiptEntity, Long> {

    /**
     * Find receipt by receipt number
     */
    Optional<PosReceiptEntity> findByReceiptNumber(String receiptNumber);

    /**
     * Find receipt by order ID
     */
    Optional<PosReceiptEntity> findByOrderId(Long orderId);

    /**
     * Check if receipt number exists
     */
    boolean existsByReceiptNumber(String receiptNumber);

    /**
     * Get count of receipts generated today (for sequence generation)
     */
    @Query("SELECT COUNT(r) FROM PosReceiptEntity r WHERE r.createdAt >= :startOfDay")
    long countReceiptsToday(@Param("startOfDay") LocalDateTime startOfDay);
}
