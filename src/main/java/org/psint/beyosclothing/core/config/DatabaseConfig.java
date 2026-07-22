package org.psint.beyosclothing.core.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Database Auditing Configuration
 * Enables JPA auditing for BaseEntity (dateCreated, dateUpdated)
 *
 * NOTE: This config does NOT handle repository scanning or transaction management.
 * Those are handled by:
 * - AuthDatabaseConfig (for auth module)
 * - CustomerDatabaseConfig (for customer module)
 */
@Configuration
@EnableJpaAuditing
public class DatabaseConfig {
    // JPA Auditing only - @PrePersist and @PreUpdate hooks
    // Multi-database configuration is in AuthDatabaseConfig and CustomerDatabaseConfig
}
