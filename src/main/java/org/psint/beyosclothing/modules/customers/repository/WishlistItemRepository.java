package org.psint.beyosclothing.modules.customers.repository;

import org.psint.beyosclothing.modules.customers.entity.WishlistItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {

    Optional<WishlistItem> findByUuid(String uuid);

    Optional<WishlistItem> findByUuidAndIsAvailableTrue(String uuid);

    Page<WishlistItem> findByCustomerIdAndIsAvailableTrue(Long customerId, Pageable pageable);

    Boolean existsByCustomerIdAndProductIdAndIsAvailableTrue(Long customerId, Long productId);

    Optional<WishlistItem> findByCustomerIdAndProductId(Long customerId, Long productId);

    Long countByCustomerIdAndIsAvailableTrue(Long customerId);

    void deleteByCustomerIdAndProductId(Long customerId, Long productId);
}

