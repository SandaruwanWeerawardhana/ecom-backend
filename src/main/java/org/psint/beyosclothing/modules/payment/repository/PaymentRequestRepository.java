package org.psint.beyosclothing.modules.payment.repository;

import org.psint.beyosclothing.modules.payment.entity.PaymentRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Payment Request Repository
 * Database: beyos_payment
 */
@Repository
public interface PaymentRequestRepository extends JpaRepository<PaymentRequestEntity, Long> {

    Optional<PaymentRequestEntity> findByUuid(String uuid);

    Optional<PaymentRequestEntity> findByGatewayTransactionId(String gatewayTransactionId);

    List<PaymentRequestEntity> findByOrderId(Long orderId);

    List<PaymentRequestEntity> findByMethodId(Long methodId);

    List<PaymentRequestEntity> findByStatus(PaymentRequestEntity.PaymentRequestStatus status);

    Optional<PaymentRequestEntity> findByOrderIdAndStatus(Long orderId, PaymentRequestEntity.PaymentRequestStatus status);
}

