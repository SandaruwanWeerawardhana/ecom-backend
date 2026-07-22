package org.psint.beyosclothing.core.config;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
 * Promotion Database Configuration
 * Database: beyos_promo_db (SEPARATE DATABASE)
 * Entities: promotion module entities
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
    basePackages = "org.psint.beyosclothing.modules.promotions.repository",
    entityManagerFactoryRef = "promotionEntityManagerFactory",
    transactionManagerRef = "promotionTransactionManager"
)
public class PromotionDatabaseConfig {

    /**
     * Promotion DataSource
     * Connects to: beyos_promo_db
     */
    @Bean(name = "promotionDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.promotion")
    public DataSource promotionDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    /**
     * Flyway for Promotion Database ONLY
     * Location: db/migration_promotion
     */
    @Bean(name = "promotionFlyway")
    public Flyway promotionFlyway(@Qualifier("promotionDataSource") DataSource promotionDataSource) {
        Flyway flyway = Flyway.configure()
            .dataSource(promotionDataSource)
            .locations("classpath:db/migration_promotion")
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
     * Promotion EntityManagerFactory
     * Scans: org.psint.beyosclothing.modules.promotions.entity
     */
    @Bean(name = "promotionEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean promotionEntityManagerFactory(
            @Qualifier("promotionDataSource") DataSource promotionDataSource,
            @Qualifier("promotionFlyway") Flyway promotionFlyway) {

        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(promotionDataSource);
        em.setPackagesToScan("org.psint.beyosclothing.modules.promotions.entity");
        em.setPersistenceUnitName("promotion");

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
     * Promotion TransactionManager
     * Use with: @Transactional("promotionTransactionManager")
     */
    @Bean(name = "promotionTransactionManager")
    public PlatformTransactionManager promotionTransactionManager(
            @Qualifier("promotionEntityManagerFactory") EntityManagerFactory promotionEntityManagerFactory) {
        return new JpaTransactionManager(promotionEntityManagerFactory);
    }
}
