package org.psint.beyosclothing.modules.auth.repository;

import org.psint.beyosclothing.modules.auth.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Password Reset Token Repository
 * Database: beyos_auth_db
 */
@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Query("SELECT prt FROM PasswordResetToken prt WHERE prt.tokenHash = :tokenHash AND prt.isUsed = false AND prt.expiresAt > :now")
    Optional<PasswordResetToken> findValidToken(@Param("tokenHash") String tokenHash, @Param("now") LocalDateTime now);

    @Query("SELECT COUNT(prt) FROM PasswordResetToken prt WHERE prt.user.id = :userId AND prt.dateCreated >= :startDate")
    Long countRecentTokensByUserId(@Param("userId") Long userId, @Param("startDate") LocalDateTime startDate);

    void deleteByUserIdAndIsUsedTrue(Long userId);
}

