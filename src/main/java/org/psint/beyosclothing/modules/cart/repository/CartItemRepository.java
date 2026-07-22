package org.psint.beyosclothing.modules.cart.repository;

import org.psint.beyosclothing.modules.cart.entity.CartItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Cart Item Repository
 * Database: beyos_cart_db
 */
@Repository
public interface CartItemRepository extends JpaRepository<CartItemEntity, Long> {

    /**
     * Find cart item by UUID
     * @param uuid Cart item UUID
     * @return Optional cart item
     */
    Optional<CartItemEntity> findByUuid(String uuid);

    List<CartItemEntity> findByCartIdAndIsActiveTrue(Long cartId);

    List<CartItemEntity> findByCartId(Long cartId);

    Optional<CartItemEntity> findByCartIdAndProductIdAndVariantIdAndIsActiveTrue(
            Long cartId, Long productId, Long variantId);

    Optional<CartItemEntity> findByCartIdAndProductIdAndVariantIdIsNullAndIsActiveTrue(
            Long cartId, Long productId);

    @Query("SELECT ci FROM CartItemEntity ci WHERE ci.cartId = :cartId AND ci.isActive = true")
    List<CartItemEntity> findActiveItemsByCartId(@Param("cartId") Long cartId);

    @Query("SELECT COUNT(ci) FROM CartItemEntity ci WHERE ci.cartId = :cartId AND ci.isActive = true")
    Long countActiveItemsByCartId(@Param("cartId") Long cartId);

    List<CartItemEntity> findByCartIdAndUuidInAndIsActiveTrue(Long cartId, List<String> uuids);

    void deleteByCartId(Long cartId);
}
