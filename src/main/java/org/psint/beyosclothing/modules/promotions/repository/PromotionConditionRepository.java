package org.psint.beyosclothing.modules.promotions.repository;

import org.psint.beyosclothing.modules.promotions.entity.PromotionCondition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Promotion Condition Repository
 */
@Repository
public interface PromotionConditionRepository extends JpaRepository<PromotionCondition, Long> {

    Optional<PromotionCondition> findByUuid(String uuid);

    List<PromotionCondition> findAllByPromotionIdAndIsActiveTrue(Long promotionId);

    List<PromotionCondition> findAllByPromotionId(Long promotionId);

    void deleteAllByPromotionId(Long promotionId);
}

