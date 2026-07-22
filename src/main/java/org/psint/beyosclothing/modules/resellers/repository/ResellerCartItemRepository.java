package org.psint.beyosclothing.modules.resellers.repository;

import org.psint.beyosclothing.modules.resellers.entity.ResellerCartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for ResellerCartItem entity
 */
@Repository
public interface ResellerCartItemRepository extends JpaRepository<ResellerCartItem, Long> {

    List<ResellerCartItem> findByCartIdAndIsActive(Long cartId, Boolean isActive);

    List<ResellerCartItem> findByCartIdAndIsActiveTrue(Long cartId);

    List<ResellerCartItem> findByCartId(Long cartId);

    Optional<ResellerCartItem> findByCartIdAndUuidAndIsActive(Long cartId, String uuid, Boolean isActive);

    @Modifying
    @Query("UPDATE ResellerCartItem ci SET ci.isActive = false WHERE ci.cartId = :cartId AND ci.isActive = true")
    void softDeleteByCartId(@Param("cartId") Long cartId);

    Optional<ResellerCartItem> findByCartIdAndProductIdAndVariantIdAndIsActive(Long cartId, Long productId, Long variantId, Boolean isActive);

    @Query("SELECT ci FROM ResellerCartItem ci WHERE ci.cartId = :cartId AND ci.productId = :productId AND ci.variantId IS NULL AND ci.isActive = true")
    Optional<ResellerCartItem> findByCartIdAndProductIdAndVariantIdIsNullAndIsActive(@Param("cartId") Long cartId, @Param("productId") Long productId);
}
