package org.psint.beyosclothing.modules.delivery.repository;

import org.psint.beyosclothing.modules.delivery.entity.Courier;
import org.psint.beyosclothing.modules.delivery.entity.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    Optional<Shipment> findByUuid(UUID uuid);

    Optional<Shipment> findByUuid(String uuid);

    Optional<Shipment> findByTrackingNumber(String trackingNumber);

    Optional<Shipment> findByWayBillId(String wayBillId);

    Optional<Shipment> findByOrderId(Long orderId);

    List<Shipment> findByCourier(Courier courier);

    List<Shipment> findByStatus(Shipment.ShipmentStatus status);

    List<Shipment> findByPayerType(Shipment.PayerType payerType);

    // Changed from findByPaymentMethod to findByPaymentMethodId
    List<Shipment> findByPaymentMethodId(Long paymentMethodId);

    @Query("SELECT s FROM Shipment s WHERE s.courier = :courier AND s.status = :status")
    List<Shipment> findByCourierAndStatus(
        @Param("courier") Courier courier,
        @Param("status") Shipment.ShipmentStatus status
    );

    @Query("SELECT s FROM Shipment s WHERE s.bookedAt BETWEEN :startDate AND :endDate")
    List<Shipment> findByBookedAtBetween(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    @Query("SELECT s FROM Shipment s WHERE s.deliveredAt BETWEEN :startDate AND :endDate")
    List<Shipment> findByDeliveredAtBetween(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    boolean existsByTrackingNumber(String trackingNumber);

    // Additional useful methods
    List<Shipment> findByPaymentMethodIdAndPayerType(Long paymentMethodId, Shipment.PayerType payerType);

    List<Shipment> findByCourierAndPaymentMethodId(Courier courier, Long paymentMethodId);

    @Query("SELECT s FROM Shipment s WHERE s.status = :status AND s.dateCreated >= :since ORDER BY s.dateCreated DESC")
    List<Shipment> findRecentShipmentsByStatus(
        @Param("status") Shipment.ShipmentStatus status,
        @Param("since") LocalDateTime since
    );
}
