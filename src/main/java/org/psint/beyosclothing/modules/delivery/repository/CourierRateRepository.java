package org.psint.beyosclothing.modules.delivery.repository;

import org.psint.beyosclothing.modules.delivery.entity.Courier;
import org.psint.beyosclothing.modules.delivery.entity.CourierRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourierRateRepository extends JpaRepository<CourierRate, Long> {
    Optional<CourierRate> findByUuid(String uuid);

    List<CourierRate> findByCourier(Courier courier);

    // Find rates by courier and customer type
    List<CourierRate> findByCourierAndCustomerType(Courier courier, CourierRate.CustomerType customerType);

    // Find rates by courier, customer type, and payment method
    List<CourierRate> findByCourierAndCustomerTypeAndPaymentMethodId(
            Courier courier,
            CourierRate.CustomerType customerType,
            Long paymentMethodId
    );

    // Find rates by courier and customer type where payment method is null (applies to all)
    List<CourierRate> findByCourierAndCustomerTypeAndPaymentMethodIdIsNull(
            Courier courier,
            CourierRate.CustomerType customerType
    );

    // Query to find the best matching rate with priority
    @Query("SELECT cr FROM CourierRate cr WHERE cr.courier = :courier " +
           "AND cr.customerType = :customerType " +
           "AND (cr.paymentMethodId = :paymentMethodId OR cr.paymentMethodId IS NULL) " +
           "AND cr.isActive = true " +
           "AND (cr.effectiveFrom IS NULL OR cr.effectiveFrom <= CURRENT_TIMESTAMP) " +
           "AND (cr.effectiveTo IS NULL OR cr.effectiveTo >= CURRENT_TIMESTAMP) " +
           "ORDER BY cr.paymentMethodId DESC, cr.dateCreated DESC")
    List<CourierRate> findApplicableRates(
            @Param("courier") Courier courier,
            @Param("customerType") CourierRate.CustomerType customerType,
            @Param("paymentMethodId") Long paymentMethodId
    );

    @Query("SELECT cr FROM CourierRate cr WHERE cr.courier = :courier " +
           "AND cr.customerType = :customerType " +
           "AND cr.isActive = true " +
           "AND (cr.effectiveFrom IS NULL OR cr.effectiveFrom <= CURRENT_TIMESTAMP) " +
           "AND (cr.effectiveTo IS NULL OR cr.effectiveTo >= CURRENT_TIMESTAMP) " +
           "ORDER BY cr.dateCreated DESC")
    List<CourierRate> findApplicableRates(
            @Param("courier") Courier courier,
            @Param("customerType") CourierRate.CustomerType customerType
    );
}
