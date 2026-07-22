package org.psint.beyosclothing.modules.payment.repository;

import org.psint.beyosclothing.modules.payment.entity.PaymentMethodFeeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentMethodFeeRepository extends JpaRepository<PaymentMethodFeeEntity, Long> {

    boolean existsByPaymentMethodIdAndCustomerType(Long paymentMethodId, PaymentMethodFeeEntity.CustomerType customerType);

    List<PaymentMethodFeeEntity> findByPaymentMethodId(Long paymentMethodId);

    List<PaymentMethodFeeEntity> findAllByPaymentMethodIdAndCustomerTypeAndIsActiveTrue(Long paymentMethodId, PaymentMethodFeeEntity.CustomerType customerType);
}
