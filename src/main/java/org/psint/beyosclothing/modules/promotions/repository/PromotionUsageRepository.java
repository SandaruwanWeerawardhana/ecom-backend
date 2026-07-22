package org.psint.beyosclothing.modules.promotions.repository;

import org.psint.beyosclothing.modules.promotions.entity.PromotionUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Promotion Usage Repository
 */
@Repository
public interface PromotionUsageRepository extends JpaRepository<PromotionUsage, Long> {

    Optional<PromotionUsage> findByUuid(String uuid);

    @Query("SELECT COUNT(pu) FROM PromotionUsage pu WHERE pu.promotionId = :promotionId AND pu.isActive = true")
    long countByPromotionId(@Param("promotionId") Long promotionId);

    @Query("SELECT COUNT(pu) FROM PromotionUsage pu WHERE pu.promotionId = :promotionId AND pu.customerId = :customerId AND pu.isActive = true")
    long countByPromotionIdAndCustomerId(@Param("promotionId") Long promotionId, @Param("customerId") Long customerId);
}

