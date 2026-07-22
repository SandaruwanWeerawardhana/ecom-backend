package org.psint.beyosclothing.modules.orders.repository;

import org.psint.beyosclothing.modules.orders.entity.OrderPaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderPaymentRepository extends JpaRepository<OrderPaymentEntity, Long> {

    List<OrderPaymentEntity> findByOrderId(Long orderId);
}

