package org.psint.beyosclothing.modules.promotions.repository;

import org.psint.beyosclothing.modules.promotions.entity.PromotionProductMap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Promotion Product Map Repository
 */
@Repository
public interface PromotionProductMapRepository extends JpaRepository<PromotionProductMap, Long> {

    Optional<PromotionProductMap> findByUuid(String uuid);

    List<PromotionProductMap> findAllByPromotionIdAndIsActiveTrue(Long promotionId);

    List<PromotionProductMap> findAllByProductIdAndIsActiveTrue(Long productId);

    void deleteAllByPromotionId(Long promotionId);

    boolean existsByPromotionIdAndProductId(Long promotionId, Long productId);
}

