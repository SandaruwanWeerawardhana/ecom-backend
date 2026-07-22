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
 * Inventory Database Configuration
 * Database: beyos_inventory_db (SEPARATE DATABASE)
 * Entities: inventory module entities
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
    basePackages = "org.psint.beyosclothing.modules.inventory.repository",
    entityManagerFactoryRef = "inventoryEntityManagerFactory",
    transactionManagerRef = "inventoryTransactionManager"
)
public class InventoryDatabaseConfig {

    /**
     * Inventory DataSource
     * Connects to: beyos_inventory_db
     */
    @Bean(name = "inventoryDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.inventory")
    public DataSource inventoryDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    /**
     * Flyway for Inventory Database ONLY
     * Location: db/migration_inventory
     */
    @Bean(name = "inventoryFlyway")
    public Flyway inventoryFlyway(@Qualifier("inventoryDataSource") DataSource inventoryDataSource) {
        Flyway flyway = Flyway.configure()
            .dataSource(inventoryDataSource)
            .locations("classpath:db/migration_inventory")
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
     * Inventory EntityManagerFactory
     * Scans: org.psint.beyosclothing.modules.inventory.entity
     */
    @Bean(name = "inventoryEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean inventoryEntityManagerFactory(
            @Qualifier("inventoryDataSource") DataSource inventoryDataSource,
            @Qualifier("inventoryFlyway") Flyway inventoryFlyway) {

        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(inventoryDataSource);
        em.setPackagesToScan("org.psint.beyosclothing.modules.inventory.entity");
        em.setPersistenceUnitName("inventory");

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
     * Inventory TransactionManager
     * Use with: @Transactional("inventoryTransactionManager")
     */
    @Bean(name = "inventoryTransactionManager")
    public PlatformTransactionManager inventoryTransactionManager(
            @Qualifier("inventoryEntityManagerFactory") EntityManagerFactory inventoryEntityManagerFactory) {
        return new JpaTransactionManager(inventoryEntityManagerFactory);
    }
}
