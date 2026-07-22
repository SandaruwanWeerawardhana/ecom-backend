package org.psint.beyosclothing.modules.pos.repository;

import org.psint.beyosclothing.modules.pos.entity.PosTerminalEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * POS Terminal Repository
 * Manages CRUD operations for POS terminals
 */
@Repository
public interface PosTerminalRepository extends JpaRepository<PosTerminalEntity, Long> {

    /**
     * Find terminal by UUID
     */
    Optional<PosTerminalEntity> findByUuid(String uuid);

    /**
     * Find terminal by code
     */
    Optional<PosTerminalEntity> findByCode(String code);

    /**
     * Find all active terminals
     */
    List<PosTerminalEntity> findByIsActiveTrue(Pageable pageable);

    /**
     * Count active terminals for correct pagination
     */
    long countByIsActiveTrue();

}
