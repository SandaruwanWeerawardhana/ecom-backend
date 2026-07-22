package org.psint.beyosclothing.modules.promotions.repository;

import org.psint.beyosclothing.modules.promotions.entity.PromotionAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Promotion Action Repository
 */
@Repository
public interface PromotionActionRepository extends JpaRepository<PromotionAction, Long> {

    Optional<PromotionAction> findByUuid(String uuid);

    List<PromotionAction> findAllByPromotionIdAndIsActiveTrue(Long promotionId);

    List<PromotionAction> findAllByPromotionId(Long promotionId);

    List<PromotionAction> findAllByIsActiveTrue();

    void deleteAllByPromotionId(Long promotionId);
}
