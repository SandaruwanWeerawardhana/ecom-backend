package org.psint.beyosclothing.modules.resellers.repository;

import org.psint.beyosclothing.modules.resellers.entity.ResellerCart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for ResellerCart entity
 */
@Repository
public interface ResellerCartRepository extends JpaRepository<ResellerCart, Long> {

    Optional<ResellerCart> findByResellerIdAndIsActive(Long resellerId, Boolean isActive);

    Optional<ResellerCart> findByUuid(String uuid);

    @Query("SELECT COUNT(ci) FROM ResellerCartItem ci WHERE ci.cartId IN " +
           "(SELECT c.id FROM ResellerCart c WHERE c.resellerId = :resellerId AND c.isActive = true)")
    Long countActiveItemsByResellerId(@Param("resellerId") Long resellerId);
}

