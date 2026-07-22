package org.psint.beyosclothing.modules.delivery.repository;

import org.psint.beyosclothing.modules.delivery.entity.Courier;
import org.psint.beyosclothing.modules.delivery.entity.CourierPaymentMethodMap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CourierPaymentMethodMapRepository extends JpaRepository<CourierPaymentMethodMap, Long> {

    Optional<CourierPaymentMethodMap> findByUuid(UUID uuid);

    List<CourierPaymentMethodMap> findByCourier(Courier courier);

    // Changed from findByPaymentMethod to findByPaymentMethodId
    List<CourierPaymentMethodMap> findByPaymentMethodId(Long paymentMethodId);

    // Changed from findByCourierAndPaymentMethod to findByCourierAndPaymentMethodId
    Optional<CourierPaymentMethodMap> findByCourierAndPaymentMethodId(Courier courier, Long paymentMethodId);

    // NEW: Method to find by courier ID and payment method ID (both Long values)
    Optional<CourierPaymentMethodMap> findByCourierIdAndPaymentMethodId(Long courierId, Long paymentMethodId);

    List<CourierPaymentMethodMap> findByCourierAndIsFeeFree(Courier courier, Boolean isFeeFree);

    // Changed from existsByCourierAndPaymentMethod to existsByCourierAndPaymentMethodId
    boolean existsByCourierAndPaymentMethodId(Courier courier, Long paymentMethodId);

    // Additional useful methods
    List<CourierPaymentMethodMap> findByIsFeeFree(Boolean isFeeFree);

    List<CourierPaymentMethodMap> findByPaymentMethodIdAndIsFeeFree(Long paymentMethodId, Boolean isFeeFree);
}
