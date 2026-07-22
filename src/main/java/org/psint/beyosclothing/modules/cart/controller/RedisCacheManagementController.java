package org.psint.beyosclothing.modules.cart.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * TEMPORARY: Redis Cache Management Controller
 * Used to clear incompatible cache data after Redis configuration changes
 * DELETE THIS FILE AFTER CACHE IS CLEARED
 */
@RestController
@RequestMapping("/api/admin/cache")
@RequiredArgsConstructor
@Slf4j
public class RedisCacheManagementController {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Clear all cart-related cache
     * Call this endpoint once after updating RedisConfig
     */
    @DeleteMapping("/clear-cart")
    public ResponseEntity<String> clearCartCache() {
        try {
            Set<String> keys = redisTemplate.keys("cart:*");
            if (keys != null && !keys.isEmpty()) {
                Long deleted = redisTemplate.delete(keys);
                log.info("Cleared {} cart cache keys", deleted);
                return ResponseEntity.ok("Cleared " + deleted + " cart cache keys");
            }
            return ResponseEntity.ok("No cart cache keys found");
        } catch (Exception e) {
            log.error("Error clearing cart cache", e);
            return ResponseEntity.internalServerError()
                    .body("Error: " + e.getMessage());
        }
    }

    /**
     * Clear ALL Redis cache (use with caution)
     */
    @DeleteMapping("/clear-all")
    public ResponseEntity<String> clearAllCache() {
        try {
            redisTemplate.getConnectionFactory().getConnection().flushAll();
            log.warn("Cleared ALL Redis cache");
            return ResponseEntity.ok("All Redis cache cleared successfully");
        } catch (Exception e) {
            log.error("Error clearing all cache", e);
            return ResponseEntity.internalServerError()
                    .body("Error: " + e.getMessage());
        }
    }
}

