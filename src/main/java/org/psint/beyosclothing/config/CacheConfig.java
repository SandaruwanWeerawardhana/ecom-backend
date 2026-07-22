package org.psint.beyosclothing.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Cache Configuration for Product Cards and other frequently accessed data
 * Uses Caffeine as in-memory cache provider
 */
@Configuration
@EnableCaching
@Slf4j
public class CacheConfig {

    /**
     * Configure Caffeine cache manager for product cards
     * Cache will store up to 1000 entries and expire after 5 minutes
     */
    @Bean
    public CacheManager caffeineCacheManager() {
        log.info("Initializing Caffeine cache manager for product cards");

        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
            "productCards",      // Cache for homepage product cards
            "productDetails",    // Cache for individual product details
            "productCategories", // Cache for category lists
            "categories",        // Cache for individual categories
            "mainCategories",    // Cache for main categories
            "subCategories",     // Cache for sub-categories
            "categoryHierarchy", // Cache for category hierarchy
            "couriers",          // Cache for individual couriers
            "activeCouriers",    // Cache for list of active couriers
            "courierRate",       // Cache for individual courier rates
            "courierRates"       // Cache for list of courier rates
        );

        cacheManager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(1000)                          // Max 1000 entries
            .expireAfterWrite(5, TimeUnit.MINUTES)      // Expire after 5 minutes
            .recordStats());                             // Enable statistics for monitoring

        log.info("Cache manager initialized successfully with 5-minute TTL");
        return cacheManager;
    }
}
