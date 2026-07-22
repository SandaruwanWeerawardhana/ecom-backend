package org.psint.beyosclothing.modules.resellers.repository;

import org.psint.beyosclothing.modules.resellers.entity.ResellerPriceOverride;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for ResellerPriceOverride entity
 */
@Repository
public interface ResellerPriceOverrideRepository extends JpaRepository<ResellerPriceOverride, Long> {

    List<ResellerPriceOverride> findByOrderId(Long orderId);

    Page<ResellerPriceOverride> findByResellerId(Long resellerId, Pageable pageable);

    List<ResellerPriceOverride> findByResellerIdAndOrderId(Long resellerId, Long orderId);
}

