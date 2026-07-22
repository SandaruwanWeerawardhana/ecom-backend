package org.psint.beyosclothing.modules.auth.repository;

import org.psint.beyosclothing.modules.auth.entity.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Email Verification Token Repository
 * Database: beyos_auth_db
 */
@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    @Query("SELECT evt FROM EmailVerificationToken evt WHERE evt.tokenHash = :tokenHash AND evt.isUsed = false AND evt.expiresAt > :now")
    Optional<EmailVerificationToken> findValidToken(@Param("tokenHash") String tokenHash, @Param("now") LocalDateTime now);

    @Query("SELECT COUNT(evt) FROM EmailVerificationToken evt WHERE evt.user.id = :userId AND evt.dateCreated >= :startDate")
    Long countRecentTokensByUserId(@Param("userId") Long userId, @Param("startDate") LocalDateTime startDate);

    Optional<EmailVerificationToken> findByUserId(Long userId);

    void deleteByUserIdAndIsUsedTrue(Long userId);
}

