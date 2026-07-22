package org.psint.beyosclothing.modules.cart.repository;

import org.psint.beyosclothing.modules.cart.entity.GuestCartMappingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Guest Cart Mapping Repository
 * Database: beyos_cart_db
 */
@Repository
public interface GuestCartMappingRepository extends JpaRepository<GuestCartMappingEntity, Long> {

    Optional<GuestCartMappingEntity> findByUuid(String uuid);

    Optional<GuestCartMappingEntity> findByGuestSessionToken(String guestSessionToken);

    Optional<GuestCartMappingEntity> findByCartId(Long cartId);

    Optional<GuestCartMappingEntity> findByCartUuid(String cartUuid);

    List<GuestCartMappingEntity> findByIsMergedFalseAndIsActiveTrue();

    boolean existsByGuestSessionToken(String guestSessionToken);
}

