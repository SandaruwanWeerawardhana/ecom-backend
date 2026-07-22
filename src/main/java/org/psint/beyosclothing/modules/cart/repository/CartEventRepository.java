package org.psint.beyosclothing.modules.cart.repository;

import org.psint.beyosclothing.modules.cart.entity.CartEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Cart Event Repository
 * Database: beyos_cart_db
 */
@Repository
public interface CartEventRepository extends JpaRepository<CartEventEntity, Long> {

    Optional<CartEventEntity> findByUuid(String uuid);

    List<CartEventEntity> findByCartIdOrderByDateCreatedDesc(Long cartId);

    List<CartEventEntity> findByEventTypeOrderByDateCreatedDesc(String eventType);

    @Query("SELECT ce FROM CartEventEntity ce WHERE ce.cartId = :cartId AND ce.dateCreated >= :since ORDER BY ce.dateCreated DESC")
    List<CartEventEntity> findRecentEventsByCartId(@Param("cartId") Long cartId, @Param("since") LocalDateTime since);
}

