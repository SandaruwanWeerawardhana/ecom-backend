package org.psint.beyosclothing.modules.promotions.repository;

import org.psint.beyosclothing.modules.promotions.entity.Promotion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Promotion Repository
 */
@Repository
public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    Optional<Promotion> findByUuid(String uuid);

    Optional<Promotion> findByPromoCode(String promoCode);

    Optional<Promotion> findByPromoCodeAndIsActiveTrue(String promoCode);

    Page<Promotion> findAllByIsActiveTrue(Pageable pageable);

    @Query("SELECT p FROM Promotion p WHERE p.isActive = true AND p.startAt <= :now AND (p.endAt IS NULL OR p.endAt >= :now)")
    Page<Promotion> findActivePromotions(@Param("now") LocalDateTime now, Pageable pageable);

    @Query("SELECT p FROM Promotion p WHERE p.promoCode = :code AND p.isActive = true AND p.startAt <= :now AND (p.endAt IS NULL OR p.endAt >= :now)")
    Optional<Promotion> findValidPromotionByCode(@Param("code") String code, @Param("now") LocalDateTime now);

    boolean existsByPromoCode(String promoCode);

    @Query("SELECT COUNT(p) FROM Promotion p WHERE p.isActive = true AND p.startAt <= :now AND (p.endAt IS NULL OR p.endAt >= :now)")
    long countActivePromotions(@Param("now") LocalDateTime now);
}

