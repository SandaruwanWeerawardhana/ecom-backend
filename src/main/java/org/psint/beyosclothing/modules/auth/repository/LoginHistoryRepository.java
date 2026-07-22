package org.psint.beyosclothing.modules.auth.repository;

import org.psint.beyosclothing.modules.auth.entity.LoginHistory;
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
 * Login History Repository
 * Database: beyos_auth_db
 */
@Repository
public interface LoginHistoryRepository extends JpaRepository<LoginHistory, Long> {

    Optional<LoginHistory> findByLoginId(String loginId);

    Page<LoginHistory> findByUserId(Long userId, Pageable pageable);

    @Query("SELECT lh FROM LoginHistory lh WHERE lh.user.id = :userId AND lh.loginTime >= :startDate ORDER BY lh.loginTime DESC")
    List<LoginHistory> findRecentLoginsByUserId(@Param("userId") Long userId, @Param("startDate") LocalDateTime startDate);

    @Query("SELECT lh FROM LoginHistory lh WHERE lh.ipAddress = :ipAddress AND lh.loginTime >= :startDate")
    List<LoginHistory> findByIpAddressAndTimeRange(@Param("ipAddress") String ipAddress, @Param("startDate") LocalDateTime startDate);

    @Query("SELECT COUNT(lh) FROM LoginHistory lh WHERE lh.user.id = :userId AND lh.isLoginSuccess = false AND lh.loginTime >= :startDate")
    Long countFailedLoginAttempts(@Param("userId") Long userId, @Param("startDate") LocalDateTime startDate);
}

