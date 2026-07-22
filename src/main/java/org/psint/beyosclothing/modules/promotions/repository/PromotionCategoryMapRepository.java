package org.psint.beyosclothing.modules.promotions.repository;

import org.psint.beyosclothing.modules.promotions.entity.PromotionCategoryMap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Promotion Category Map Repository
 */
@Repository
public interface PromotionCategoryMapRepository extends JpaRepository<PromotionCategoryMap, Long> {

    Optional<PromotionCategoryMap> findByUuid(String uuid);

    List<PromotionCategoryMap> findAllByPromotionIdAndIsActiveTrue(Long promotionId);

    List<PromotionCategoryMap> findAllByCategoryIdAndIsActiveTrue(Long categoryId);

    void deleteAllByPromotionId(Long promotionId);

    boolean existsByPromotionIdAndCategoryId(Long promotionId, Long categoryId);
}

