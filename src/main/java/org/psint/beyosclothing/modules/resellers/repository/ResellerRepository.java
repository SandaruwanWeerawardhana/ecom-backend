package org.psint.beyosclothing.modules.resellers.repository;

import org.psint.beyosclothing.modules.resellers.entity.Reseller;
import org.psint.beyosclothing.modules.resellers.entity.ResellerStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for Reseller entity
 */
@Repository
public interface ResellerRepository extends JpaRepository<Reseller, Long> {

    Optional<Reseller> findByUuid(String uuid);

    Optional<Reseller> findByUserId(Long userId);

    Optional<Reseller> findByEmailAndIsActiveTrue(String email);

    boolean existsByEmailAndIsActiveTrue(String email);

    boolean existsByUserId(Long userId);

    Page<Reseller> findByStatus(ResellerStatus status, Pageable pageable);

    Page<Reseller> findByStatusAndIsActive(ResellerStatus status, Boolean isActive, Pageable pageable);

    Page<Reseller> findByIsActive(Boolean isActive, Pageable pageable);
}

