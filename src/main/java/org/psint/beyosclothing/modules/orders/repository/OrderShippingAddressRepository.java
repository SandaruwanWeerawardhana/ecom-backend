package org.psint.beyosclothing.modules.orders.repository;

import org.psint.beyosclothing.modules.orders.entity.OrderShippingAddressEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderShippingAddressRepository extends JpaRepository<OrderShippingAddressEntity, Long> {

    Optional<OrderShippingAddressEntity> findByOrderId(Long orderId);

    /**
     * Batch-loads shipping addresses for several orders at once, so the admin order list can resolve
     * customer names for a whole page in a single query instead of one lookup per order.
     */
    List<OrderShippingAddressEntity> findByOrderIdIn(Collection<Long> orderIds);
}

