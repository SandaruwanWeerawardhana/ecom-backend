package org.psint.beyosclothing.modules.delivery.repository;

import org.psint.beyosclothing.modules.delivery.entity.Courier;
import org.psint.beyosclothing.modules.delivery.entity.CourierApiLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CourierApiLogRepository extends JpaRepository<CourierApiLog, Long> {

    Optional<CourierApiLog> findByUuid(UUID uuid);

    List<CourierApiLog> findByCourier(Courier courier);

    List<CourierApiLog> findByHttpStatus(Integer httpStatus);

    List<CourierApiLog> findByCourierOrderByDateCreatedDesc(Courier courier);

    @Query("SELECT l FROM CourierApiLog l WHERE l.courier = :courier AND l.httpStatus = :httpStatus")
    List<CourierApiLog> findByCourierAndHttpStatus(
        @Param("courier") Courier courier,
        @Param("httpStatus") Integer httpStatus
    );

    @Query("SELECT l FROM CourierApiLog l WHERE l.dateCreated BETWEEN :startDate AND :endDate ORDER BY l.dateCreated DESC")
    List<CourierApiLog> findByDateCreatedBetween(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    @Query("SELECT l FROM CourierApiLog l WHERE l.courier = :courier AND l.dateCreated BETWEEN :startDate AND :endDate ORDER BY l.dateCreated DESC")
    List<CourierApiLog> findByCourierAndDateCreatedBetween(
        @Param("courier") Courier courier,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    @Query("SELECT l FROM CourierApiLog l WHERE l.httpStatus >= 400 ORDER BY l.dateCreated DESC")
    List<CourierApiLog> findFailedApiCalls();

    @Query("SELECT l FROM CourierApiLog l WHERE l.dateCreated >= :since ORDER BY l.dateCreated DESC")
    List<CourierApiLog> findRecentLogs(@Param("since") LocalDateTime since);
}

