package org.psint.beyosclothing.modules.orders.repository;

import org.psint.beyosclothing.modules.orders.entity.OrderStatusHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistoryEntity, Long> {

    List<OrderStatusHistoryEntity> findByOrderIdOrderByCreatedAtDesc(Long orderId);
    OrderStatusHistoryEntity findTopByOrderIdOrderByIdDesc(Long orderId);

}

