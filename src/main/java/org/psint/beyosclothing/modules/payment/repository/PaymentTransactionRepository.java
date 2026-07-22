package org.psint.beyosclothing.modules.payment.repository;

import org.psint.beyosclothing.modules.payment.entity.PaymentTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Payment Transaction Repository
 * Database: beyos_payment
 * Table: payment_transactions
 *
 * Used for managing payment transactions for orders
 */
@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransactionEntity, Long> {

    /**
     * Find payment transaction by UUID
     */
    Optional<PaymentTransactionEntity> findByUuid(String uuid);

    /**
     * Find payment transactions by order ID
     */
    List<PaymentTransactionEntity> findByOrderId(Long orderId);

    /**
     * Find payment transaction by order ID and status
     */
    Optional<PaymentTransactionEntity> findByOrderIdAndStatus(Long orderId, PaymentTransactionEntity.TransactionStatus status);

    /**
     * Find all payment transactions by status
     */
    List<PaymentTransactionEntity> findByStatus(PaymentTransactionEntity.TransactionStatus status);

    /**
     * Find payment transaction by payment request ID
     */
    Optional<PaymentTransactionEntity> findByPaymentRequestId(Long paymentRequestId);

    /**
     * Find payment transaction by gateway transaction ID
     */
    Optional<PaymentTransactionEntity> findByGatewayTransactionId(String gatewayTransactionId);

    /**
     * Find payment transactions by method ID
     */
    List<PaymentTransactionEntity> findByMethodId(Long methodId);
}

