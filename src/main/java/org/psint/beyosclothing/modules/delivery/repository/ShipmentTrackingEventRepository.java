package org.psint.beyosclothing.modules.delivery.repository;

import org.psint.beyosclothing.modules.delivery.entity.Shipment;
import org.psint.beyosclothing.modules.delivery.entity.ShipmentTrackingEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShipmentTrackingEventRepository extends JpaRepository<ShipmentTrackingEvent, Long> {

    Optional<ShipmentTrackingEvent> findByUuid(UUID uuid);

    List<ShipmentTrackingEvent> findByShipment(Shipment shipment);

    List<ShipmentTrackingEvent> findByShipmentOrderByEventTimeDesc(Shipment shipment);

    List<ShipmentTrackingEvent> findByStatus(Shipment.ShipmentStatus status);

    List<ShipmentTrackingEvent> findByEventSource(String eventSource);

    @Query("SELECT e FROM ShipmentTrackingEvent e WHERE e.shipment = :shipment AND e.eventTime BETWEEN :startDate AND :endDate ORDER BY e.eventTime DESC")
    List<ShipmentTrackingEvent> findByShipmentAndEventTimeBetween(
        @Param("shipment") Shipment shipment,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    @Query("SELECT e FROM ShipmentTrackingEvent e WHERE e.shipment = :shipment ORDER BY e.eventTime DESC")
    Optional<ShipmentTrackingEvent> findLatestEventByShipment(@Param("shipment") Shipment shipment);

    @Query("SELECT e FROM ShipmentTrackingEvent e WHERE e.eventTime >= :since ORDER BY e.eventTime DESC")
    List<ShipmentTrackingEvent> findRecentEvents(@Param("since") LocalDateTime since);

    @Query("SELECT e FROM ShipmentTrackingEvent e WHERE e.shipment = :shipment AND e.status = :status ORDER BY e.eventTime DESC LIMIT 1")
    Optional<ShipmentTrackingEvent> findLatestEventByShipmentAndStatus(
        @Param("shipment") Shipment shipment,
        @Param("status") Shipment.ShipmentStatus status
    );
}
