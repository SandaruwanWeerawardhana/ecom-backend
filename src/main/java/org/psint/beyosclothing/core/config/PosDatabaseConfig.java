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
 * POS Database Configuration
 * Database: beyos_pos
 * Entities: POS module entities
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
    basePackages = "org.psint.beyosclothing.modules.pos.repository",
    entityManagerFactoryRef = "posEntityManagerFactory",
    transactionManagerRef = "posTransactionManager"
)
public class PosDatabaseConfig {

    /**
     * POS DataSource
     * Connects to: beyos_pos
     */
    @Bean(name = "posDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.pos")
    public DataSource posDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    /**
     * Flyway for POS Database
     * Location: db/migration_pos
     */
    @Bean(name = "posFlyway")
    public Flyway posFlyway(@Qualifier("posDataSource") DataSource posDataSource) {
        Flyway flyway = Flyway.configure()
            .dataSource(posDataSource)
            .locations("classpath:db/migration_pos")
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
     * POS EntityManagerFactory
     * Scans: org.psint.beyosclothing.modules.pos.entity
     */
    @Bean(name = "posEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean posEntityManagerFactory(
            @Qualifier("posDataSource") DataSource posDataSource,
            @Qualifier("posFlyway") Flyway posFlyway) {

        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(posDataSource);
        em.setPackagesToScan("org.psint.beyosclothing.modules.pos.entity");
        em.setPersistenceUnitName("pos");

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
     * POS TransactionManager
     * Use with: @Transactional("posTransactionManager")
     */
    @Bean(name = "posTransactionManager")
    public PlatformTransactionManager posTransactionManager(
            @Qualifier("posEntityManagerFactory") EntityManagerFactory posEntityManagerFactory) {
        return new JpaTransactionManager(posEntityManagerFactory);
    }
}
