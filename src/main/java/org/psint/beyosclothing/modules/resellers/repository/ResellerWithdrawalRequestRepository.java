package org.psint.beyosclothing.modules.resellers.repository;

import org.psint.beyosclothing.modules.resellers.entity.ResellerWithdrawalRequest;
import org.psint.beyosclothing.modules.resellers.entity.WithdrawalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for ResellerWithdrawalRequest entity
 */
@Repository
public interface ResellerWithdrawalRequestRepository extends JpaRepository<ResellerWithdrawalRequest, Long> {

    Page<ResellerWithdrawalRequest> findByResellerIdOrderByRequestedDateDesc(Long resellerId, Pageable pageable);

    Page<ResellerWithdrawalRequest> findByStatus(WithdrawalStatus status, Pageable pageable);

    Optional<ResellerWithdrawalRequest> findByUuid(String uuid);

    @Query("SELECT w FROM ResellerWithdrawalRequest w WHERE w.resellerId = :resellerId AND w.status = 'PENDING' ORDER BY w.requestedDate DESC")
    List<ResellerWithdrawalRequest> findPendingByResellerId(@Param("resellerId") Long resellerId);

    Page<ResellerWithdrawalRequest> findByResellerIdAndStatus(Long resellerId, WithdrawalStatus status, Pageable pageable);

    @Query("SELECT w FROM ResellerWithdrawalRequest w WHERE w.resellerId = :resellerId " +
            "AND (:status IS NULL OR w.status = :status) " +
            "AND (:start IS NULL OR w.requestedDate >= :start) " +
            "AND (:end IS NULL OR w.requestedDate <= :end) " +
            "ORDER BY w.requestedDate DESC")
    Page<ResellerWithdrawalRequest> findByResellerIdAndOptionalStatusAndRequestedDateBetween(@Param("resellerId") Long resellerId,
                                                                                              @Param("status") WithdrawalStatus status,
                                                                                              @Param("start") LocalDateTime start,
                                                                                              @Param("end") LocalDateTime end,
                                                                                              Pageable pageable);

    // Admin-level
    @Query("SELECT w FROM ResellerWithdrawalRequest w WHERE " +
            "(:resellerId IS NULL OR w.resellerId = :resellerId) " +
            "AND (:status IS NULL OR w.status = :status) " +
            "AND (:start IS NULL OR w.requestedDate >= :start) " +
            "AND (:end IS NULL OR w.requestedDate <= :end) " +
            "ORDER BY w.requestedDate DESC")
    Page<ResellerWithdrawalRequest> findByOptionalResellerIdAndOptionalStatusAndRequestedDateBetween(@Param("resellerId") Long resellerId,
                                                                                                      @Param("status") WithdrawalStatus status,
                                                                                                      @Param("start") LocalDateTime start,
                                                                                                      @Param("end") LocalDateTime end,
                                                                                                      Pageable pageable);
}
