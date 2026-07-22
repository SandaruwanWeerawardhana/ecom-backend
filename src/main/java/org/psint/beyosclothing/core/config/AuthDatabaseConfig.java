package org.psint.beyosclothing.core.config;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Auth Database Configuration (Primary)
 * Database: beyos_auth_db (SEPARATE DATABASE)
 * Entities: auth module entities
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
    basePackages = "org.psint.beyosclothing.modules.auth.repository",
    entityManagerFactoryRef = "authEntityManagerFactory",
    transactionManagerRef = "authTransactionManager"
)
public class AuthDatabaseConfig {

    /**
     * Auth DataSource (Primary)
     * Connects to: beyos_auth_db
     */
    @Primary
    @Bean(name = "authDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.auth")
    public DataSource authDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    /**
     * Flyway for Auth Database ONLY
     * Location: db/migration_auth
     */
    @Bean(name = "authFlyway")
    public Flyway authFlyway(@Qualifier("authDataSource") DataSource authDataSource) {
        Flyway flyway = Flyway.configure()
            .dataSource(authDataSource)
            .locations("classpath:db/migration_auth") // Auth migrations only
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .cleanDisabled(true)
            .load();

        try {
            flyway.repair();
        } catch (Exception e) {
            System.out.println("Schema version table doesn't exist yet, proceeding with migration...");
        }
        flyway.migrate();

        return flyway;
    }

    /**
     * Auth EntityManagerFactory (Primary)
     * Scans: org.psint.beyosclothing.modules.auth.entity
     */
    @Primary
    @Bean(name = "authEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean authEntityManagerFactory(
            @Qualifier("authDataSource") DataSource authDataSource,
            @Qualifier("authFlyway") Flyway authFlyway) { // Ensure Flyway runs first

        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(authDataSource);
        em.setPackagesToScan("org.psint.beyosclothing.modules.auth.entity");
        em.setPersistenceUnitName("auth");

        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        em.setJpaVendorAdapter(vendorAdapter);

        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", "validate");
        properties.put("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");
        properties.put("hibernate.format_sql", true);
        properties.put("hibernate.physical_naming_strategy",
            "org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl");

        em.setJpaPropertyMap(properties);
        return em;
    }

    /**
     * Auth TransactionManager (Primary)
     * Use with: @Transactional or @Transactional("authTransactionManager")
     */
    @Primary
    @Bean(name = "authTransactionManager")
    public PlatformTransactionManager authTransactionManager(
            @Qualifier("authEntityManagerFactory") EntityManagerFactory authEntityManagerFactory) {
        return new JpaTransactionManager(authEntityManagerFactory);
    }
}
