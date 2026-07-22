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
 * Cart Database Configuration
 * Database: beyos_cart_db (SEPARATE DATABASE)
 * Entities: cart module entities
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
    basePackages = "org.psint.beyosclothing.modules.cart.repository",
    entityManagerFactoryRef = "cartEntityManagerFactory",
    transactionManagerRef = "cartTransactionManager"
)
public class CartDatabaseConfig {

    /**
     * Cart DataSource
     * Connects to: beyos_cart_db
     */
    @Bean(name = "cartDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.cart")
    public DataSource cartDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    /**
     * Flyway for Cart Database ONLY
     * Location: db/migration_cart
     */
    @Bean(name = "cartFlyway")
    public Flyway cartFlyway(@Qualifier("cartDataSource") DataSource cartDataSource) {
        Flyway flyway = Flyway.configure()
            .dataSource(cartDataSource)
            .locations("classpath:db/migration_cart")
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
     * Cart EntityManagerFactory
     * Scans: org.psint.beyosclothing.modules.cart.entity
     */
    @Bean(name = "cartEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean cartEntityManagerFactory(
            @Qualifier("cartDataSource") DataSource cartDataSource,
            @Qualifier("cartFlyway") Flyway cartFlyway) {

        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(cartDataSource);
        em.setPackagesToScan("org.psint.beyosclothing.modules.cart.entity");
        em.setPersistenceUnitName("cart");

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
     * Cart TransactionManager
     * Use with: @Transactional("cartTransactionManager")
     */
    @Bean(name = "cartTransactionManager")
    public PlatformTransactionManager cartTransactionManager(
            @Qualifier("cartEntityManagerFactory") EntityManagerFactory cartEntityManagerFactory) {
        return new JpaTransactionManager(cartEntityManagerFactory);
    }
}
