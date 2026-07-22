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
import org.springframework.web.client.RestTemplate;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Delivery Database Configuration
 * Database: beyos_delivery
 * Entities: delivery module entities
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
    basePackages = "org.psint.beyosclothing.modules.delivery.repository",
    entityManagerFactoryRef = "deliveryEntityManagerFactory",
    transactionManagerRef = "deliveryTransactionManager"
)
public class DeliveryDatabaseConfig {

    /**
     * Delivery DataSource
     * Connects to: beyos_delivery
     */
    @Bean(name = "deliveryDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.delivery")
    public DataSource deliveryDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    /**
     * Flyway for Delivery Database ONLY
     * Location: db/migration_delivery
     */
    @Bean(name = "deliveryFlyway")
    public Flyway deliveryFlyway(@Qualifier("deliveryDataSource") DataSource deliveryDataSource) {
        Flyway flyway = Flyway.configure()
            .dataSource(deliveryDataSource)
            .locations("classpath:db/migration_delivery")
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .cleanDisabled(true)
            .load();

        try {
            // Try to repair if schema_version table exists
            flyway.repair();
        } catch (Exception e) {
            // Schema version table doesn't exist yet, that's fine - migrate will create it
            System.out.println("Schema version table doesn't exist yet, proceeding with migration...");
        }
        
        flyway.migrate();

        return flyway;
    }

    /**
     * Delivery EntityManagerFactory
     * Scans: org.psint.beyosclothing.modules.delivery.entity
     */
    @Bean(name = "deliveryEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean deliveryEntityManagerFactory(
            @Qualifier("deliveryDataSource") DataSource deliveryDataSource,
            @Qualifier("deliveryFlyway") Flyway deliveryFlyway) {

        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(deliveryDataSource);
        em.setPackagesToScan("org.psint.beyosclothing.modules.delivery.entity");
        em.setPersistenceUnitName("delivery");

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
     * Delivery TransactionManager
     * Use with: @Transactional("deliveryTransactionManager")
     */
    @Bean(name = "deliveryTransactionManager")
    public PlatformTransactionManager deliveryTransactionManager(
            @Qualifier("deliveryEntityManagerFactory") EntityManagerFactory deliveryEntityManagerFactory) {
        return new JpaTransactionManager(deliveryEntityManagerFactory);
    }

    /**
     * RestTemplate for Delivery module — used for external courier API calls
     */
    @Bean(name = "deliveryRestTemplate")
    @Primary
    public RestTemplate deliveryRestTemplate() {
        return new RestTemplate();
    }
}
