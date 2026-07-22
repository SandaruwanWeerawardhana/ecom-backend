package org.psint.beyosclothing.modules.pos.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class PosProductSearchCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheManager cacheManager;

    private static final String REDIS_PATTERN_PREFIX = "pos:product:search:";
    private static final String CACHE_REGION = "pos-products";

    public void evictByProductName(String productName) {
        if (productName == null) return;
        String pattern = REDIS_PATTERN_PREFIX + productName.trim().toLowerCase() + "*";
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("Evicted {} keys matching pattern {}", keys.size(), pattern);
            }
        } catch (Exception e) {
            log.warn("Failed to evict cache keys for pattern {}: {}", pattern, e.getMessage());
        }

        try {
            if (cacheManager.getCache(CACHE_REGION) != null) {
                cacheManager.getCache(CACHE_REGION).clear();
                log.debug("Cleared cache region {} via CacheManager", CACHE_REGION);
            }
        } catch (Exception e) {
            log.warn("Failed to clear {} cache via CacheManager: {}", CACHE_REGION, e.getMessage());
        }
    }

    public void evictAll() {
        try {
            Set<String> keys = redisTemplate.keys(REDIS_PATTERN_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("Evicted {} keys for all pos-product search cache", keys.size());
            }
        } catch (Exception e) {
            log.warn("Failed to evict all pos product search cache keys: {}", e.getMessage());
        }

        try {
            if (cacheManager.getCache(CACHE_REGION) != null) {
                cacheManager.getCache(CACHE_REGION).clear();
            }
        } catch (Exception e) {
            log.warn("Failed to clear {} cache via CacheManager: {}", CACHE_REGION, e.getMessage());
        }
    }
}
