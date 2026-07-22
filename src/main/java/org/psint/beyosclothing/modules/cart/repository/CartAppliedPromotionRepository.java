package org.psint.beyosclothing.modules.cart.repository;

import org.psint.beyosclothing.modules.cart.entity.CartAppliedPromotionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Cart Applied Promotion Repository
 * Database: beyos_cart_db
 */
@Repository
public interface CartAppliedPromotionRepository extends JpaRepository<CartAppliedPromotionEntity, Long> {

    Optional<CartAppliedPromotionEntity> findByUuid(String uuid);

    List<CartAppliedPromotionEntity> findByCartIdAndIsActiveTrue(Long cartId);

    List<CartAppliedPromotionEntity> findByCartId(Long cartId);

    List<CartAppliedPromotionEntity> findByPromoCodeIdAndIsActiveTrue(Long promoCodeId);

    void deleteByCartId(Long cartId);
}

