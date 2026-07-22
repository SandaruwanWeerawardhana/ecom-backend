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
 * Payment Database Configuration
 * Database: beyos_payment
 * Entities: payment module entities
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
    basePackages = "org.psint.beyosclothing.modules.payment.repository",
    entityManagerFactoryRef = "paymentEntityManagerFactory",
    transactionManagerRef = "paymentTransactionManager"
)
public class PaymentDatabaseConfig {

    /**
     * Payment DataSource
     * Connects to: beyos_payment
     */
    @Bean(name = "paymentDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.payment")
    public DataSource paymentDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    /**
     * Flyway for Payment Database ONLY
     * Location: db/migration_payment
     */
    @Bean(name = "paymentFlyway")
    public Flyway paymentFlyway(@Qualifier("paymentDataSource") DataSource paymentDataSource) {
        Flyway flyway = Flyway.configure()
            .dataSource(paymentDataSource)
            .locations("classpath:db/migration_payment")
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
     * Payment EntityManagerFactory
     * Scans: org.psint.beyosclothing.modules.payment.entity
     */
    @Bean(name = "paymentEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean paymentEntityManagerFactory(
            @Qualifier("paymentDataSource") DataSource paymentDataSource,
            @Qualifier("paymentFlyway") Flyway paymentFlyway) {

        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(paymentDataSource);
        em.setPackagesToScan("org.psint.beyosclothing.modules.payment.entity");
        em.setPersistenceUnitName("payment");

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
     * Payment TransactionManager
     * Use with: @Transactional("paymentTransactionManager")
     */
    @Bean(name = "paymentTransactionManager")
    public PlatformTransactionManager paymentTransactionManager(
            @Qualifier("paymentEntityManagerFactory") EntityManagerFactory paymentEntityManagerFactory) {
        return new JpaTransactionManager(paymentEntityManagerFactory);
    }
}
