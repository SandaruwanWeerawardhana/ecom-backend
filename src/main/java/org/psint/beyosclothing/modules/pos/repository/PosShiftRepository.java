package org.psint.beyosclothing.modules.pos.repository;

import org.psint.beyosclothing.modules.pos.entity.PosShiftEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * POS Shift Repository
 * Manages CRUD operations for POS shifts
 */
@Repository
public interface PosShiftRepository extends JpaRepository<PosShiftEntity, Long> {

    /**
     * Find shift by UUID
     */
    Optional<PosShiftEntity> findByUuid(String uuid);

    /**
     * Find active shift for a specific cashier (closedAt is NULL)
     */
    Optional<PosShiftEntity> findByCashierIdAndClosedAtIsNull(Long cashierId);

    /**
     * Find all shifts for a specific cashier
     */
    List<PosShiftEntity> findByCashierId(Long cashierId);
}

