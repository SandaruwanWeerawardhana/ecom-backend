package org.psint.beyosclothing.modules.cart.repository;

import org.psint.beyosclothing.modules.cart.entity.CartEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Cart Repository
 * Database: beyos_cart_db
 */
@Repository
public interface CartRepository extends JpaRepository<CartEntity, Long> {

    Optional<CartEntity> findByUuid(String uuid);

    /**
     * Returns the most recently updated active cart for a customer.
     * Using findFirst with ordering guards against rare duplicate active carts,
     * which would otherwise cause a NonUniqueResultException.
     */
    Optional<CartEntity> findFirstByCustomerIdAndIsActiveTrueOrderByDateUpdatedDesc(Long customerId);

    List<CartEntity> findByCustomerId(Long customerId);

    List<CartEntity> findByGuestId(String guestId);

    @Query("SELECT c FROM CartEntity c WHERE c.expiresAt < :now AND c.isActive = true")
    List<CartEntity> findExpiredCarts(@Param("now") LocalDateTime now);

    boolean existsByCustomerIdAndIsActiveTrue(Long customerId);

    boolean existsByGuestIdAndIsActiveTrue(String guestId);
}

