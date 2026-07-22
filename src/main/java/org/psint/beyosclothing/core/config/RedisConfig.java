package org.psint.beyosclothing.core.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis Configuration
 * Minimal, non-deprecated config for Spring Boot 3 / Spring Data Redis 4+
 */
@Configuration
@EnableCaching
@EnableScheduling
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Use the default JSON serializer provided by Spring Data Redis
        RedisSerializer<Object> valueSerializer = RedisSerializer.json();

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(valueSerializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(valueSerializer);

        template.afterPropertiesSet();
        return template;
    }

    @Bean
    @Primary
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisSerializer<Object> valueSerializer = RedisSerializer.json();

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer));

        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        cacheConfigurations.put("attributes", defaultConfig.entryTtl(Duration.ofHours(24)));
        cacheConfigurations.put("products", defaultConfig.entryTtl(Duration.ofHours(6)));
        cacheConfigurations.put("categories", defaultConfig.entryTtl(Duration.ofHours(12)));
        cacheConfigurations.put("brands", defaultConfig.entryTtl(Duration.ofHours(12)));
        cacheConfigurations.put("customers", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigurations.put("inventory", defaultConfig.entryTtl(Duration.ofMinutes(15)));
        cacheConfigurations.put("carts", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigurations.put("orders", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigurations.put("promotions", defaultConfig.entryTtl(Duration.ofHours(2)));

        // POS-specific cache regions (optimized for high-performance POS operations)
        cacheConfigurations.put("pos-products", defaultConfig.entryTtl(Duration.ofMinutes(15))); // Product search results
        cacheConfigurations.put("pos-carts", defaultConfig.entryTtl(Duration.ofHours(4))); // Active transaction carts
        cacheConfigurations.put("pos-customers", defaultConfig.entryTtl(Duration.ofMinutes(30))); // Customer lookups
        cacheConfigurations.put("pos-terminals", defaultConfig.entryTtl(Duration.ofHours(24))); // Terminal configs (rarely change)
        cacheConfigurations.put("pos-stock", defaultConfig.entryTtl(Duration.ofMinutes(5))); // Real-time stock availability

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }
}
