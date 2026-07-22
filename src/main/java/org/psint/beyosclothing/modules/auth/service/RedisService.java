package org.psint.beyosclothing.modules.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.modules.auth.dto.SessionData;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Redis Service for JWT and Session Management
 * Handles token storage, blacklisting, and rate limiting
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedisService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String JWT_PREFIX = "jwt:session:";
    private static final String REFRESH_PREFIX = "jwt:refresh:";
    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";
    private static final String RATE_LIMIT_PREFIX = "rate:limit:";
    private static final String EMAIL_THROTTLE_PREFIX = "email:throttle:";
    private static final String SESSION_PREFIX = "session:";
    private static final String USER_SESSION_PREFIX = "user:session:";

    // JWT Session Management
    public void saveJwtSession(String username, String token, long expirationMs) {
        String key = JWT_PREFIX + username;
        redisTemplate.opsForValue().set(key, token, expirationMs, TimeUnit.MILLISECONDS);
        log.debug("JWT session saved for user: {}", username);
    }

    public String getJwtSession(String username) {
        String key = JWT_PREFIX + username;
        return (String) redisTemplate.opsForValue().get(key);
    }

    public void deleteJwtSession(String username) {
        String key = JWT_PREFIX + username;
        redisTemplate.delete(key);
        log.debug("JWT session deleted for user: {}", username);
    }

    // Refresh Token Management
    public void saveRefreshToken(String username, String refreshToken, long expirationMs) {
        String key = REFRESH_PREFIX + username;
        redisTemplate.opsForValue().set(key, refreshToken, expirationMs, TimeUnit.MILLISECONDS);
        log.debug("Refresh token saved for user: {}", username);
    }

    public String getRefreshToken(String username) {
        String key = REFRESH_PREFIX + username;
        return (String) redisTemplate.opsForValue().get(key);
    }

    public void deleteRefreshToken(String username) {
        String key = REFRESH_PREFIX + username;
        redisTemplate.delete(key);
        log.debug("Refresh token deleted for user: {}", username);
    }

    // Token Blacklist
    public void blacklistToken(String token, long expirationMs) {
        String key = BLACKLIST_PREFIX + token;
        redisTemplate.opsForValue().set(key, "blacklisted", expirationMs, TimeUnit.MILLISECONDS);
        log.debug("Token blacklisted");
    }

    public boolean isTokenBlacklisted(String token) {
        String key = BLACKLIST_PREFIX + token;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    // Rate Limiting
    public boolean isRateLimited(String identifier, int maxAttempts, Duration duration) {
        String key = RATE_LIMIT_PREFIX + identifier;
        Long attempts = redisTemplate.opsForValue().increment(key);

        if (attempts == null) {
            attempts = 0L;
        }

        if (attempts == 1) {
            redisTemplate.expire(key, duration);
        }

        boolean limited = attempts > maxAttempts;
        if (limited) {
            log.warn("Rate limit exceeded for: {}", identifier);
        }
        return limited;
    }

    public void resetRateLimit(String identifier) {
        String key = RATE_LIMIT_PREFIX + identifier;
        redisTemplate.delete(key);
    }

    // Email Throttling
    public boolean canSendEmail(String email, int maxEmails, Duration duration) {
        String key = EMAIL_THROTTLE_PREFIX + email;
        Long count = redisTemplate.opsForValue().increment(key);

        if (count == null) {
            count = 0L;
        }

        if (count == 1) {
            redisTemplate.expire(key, duration);
        }

        return count <= maxEmails;
    }

    // ==================== SESSION MANAGEMENT ====================

    /**
     * Save session data to Redis with TTL
     */
    public void saveSession(String sessionId, SessionData sessionData, long expirationMs) {
        String key = SESSION_PREFIX + sessionId;
        redisTemplate.opsForValue().set(key, sessionData, expirationMs, TimeUnit.MILLISECONDS);
        log.debug("Session saved: {}", sessionId);
    }

    /**
     * Get session data by sessionId
     */
    public SessionData getSession(String sessionId) {
        String key = SESSION_PREFIX + sessionId;
        return (SessionData) redisTemplate.opsForValue().get(key);
    }

    /**
     * Delete session by sessionId
     */
    public void deleteSession(String sessionId) {
        String key = SESSION_PREFIX + sessionId;
        redisTemplate.delete(key);
        log.debug("Session deleted: {}", sessionId);
    }

    /**
     * Update session last accessed time
     */
    public void updateSessionAccess(String sessionId, SessionData sessionData, long expirationMs) {
        String key = SESSION_PREFIX + sessionId;
        redisTemplate.opsForValue().set(key, sessionData, expirationMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Get active sessionId for a user (email-based lookup)
     */
    public String getUserActiveSessionId(String email) {
        String key = USER_SESSION_PREFIX + email;
        return (String) redisTemplate.opsForValue().get(key);
    }

    /**
     * Save user's active sessionId (for single session enforcement)
     */
    public void saveUserActiveSessionId(String email, String sessionId, long expirationMs) {
        String key = USER_SESSION_PREFIX + email;
        redisTemplate.opsForValue().set(key, sessionId, expirationMs, TimeUnit.MILLISECONDS);
        log.debug("Active session ID saved for user: {}", email);
    }

    /**
     * Delete user's active sessionId mapping
     */
    public void deleteUserActiveSessionId(String email) {
        String key = USER_SESSION_PREFIX + email;
        redisTemplate.delete(key);
    }

    /**
     * Invalidate old session when a new login occurs
     */
    public void invalidateOldSession(String email) {
        String oldSessionId = getUserActiveSessionId(email);
        if (oldSessionId != null) {
            deleteSession(oldSessionId);
            log.info("Invalidated old session: {} for user: {}", oldSessionId, email);
        }
    }

    // Generic Cache Operations
    public void set(String key, Object value, Duration duration) {
        redisTemplate.opsForValue().set(key, value, duration);
    }

    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public void delete(String key) {
        redisTemplate.delete(key);
    }

    public boolean exists(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
