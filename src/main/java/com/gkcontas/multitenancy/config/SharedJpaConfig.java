package com.gkcontas.multitenancy.config;

import com.gkcontas.multitenancy.tenant.TenantIdentifierResolver;
import jakarta.persistence.EntityManagerFactory;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.MultiTenancySettings;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * The discriminator-column persistence unit, over the single {@code shared}
 * schema.
 *
 * <p>There is no connection provider here and no {@code search_path} to switch:
 * the only thing Hibernate needs is the same tenant resolver, which it calls to
 * fill every {@code @TenantId} column on insert and to build the predicate it
 * adds to the queries it writes.
 *
 * <p>{@code default_schema} is set so the generated SQL names
 * {@code shared.customer} rather than {@code customer}. On a pool whose
 * connections have their {@code search_path} moved around, an unqualified table
 * name is a bug waiting for a schedule.
 */
@Configuration
@EnableJpaRepositories(
        basePackages = "com.gkcontas.multitenancy.shared.repository",
        entityManagerFactoryRef = "sharedEntityManagerFactory",
        transactionManagerRef = "sharedTransactionManager")
public class SharedJpaConfig {

    public static final String SCHEMA = "shared";

    @Bean
    public LocalContainerEntityManagerFactoryBean sharedEntityManagerFactory(
            DataSource dataSource, TenantIdentifierResolver tenantIdentifierResolver) {

        Map<String, Object> hibernate =
                new HashMap<>(SchemaJpaConfig.CommonHibernateProperties.base());
        hibernate.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantIdentifierResolver);
        hibernate.put(AvailableSettings.DEFAULT_SCHEMA, SCHEMA);
        hibernate.put(AvailableSettings.HBM2DDL_AUTO, "validate");
        // Hibernate's own cache keys carry the tenant identifier, which is why
        // this one does not have the bug a cache added by the application has.
        // Scenario cache-key runs the two side by side.
        hibernate.put(AvailableSettings.USE_SECOND_LEVEL_CACHE, true);
        hibernate.put(AvailableSettings.CACHE_REGION_FACTORY, "jcache");
        hibernate.put("hibernate.javax.cache.provider",
                "com.github.benmanes.caffeine.jcache.spi.CaffeineCachingProvider");

        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan("com.gkcontas.multitenancy.shared.model");
        factory.setPersistenceUnitName("discriminator-column");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(hibernate);
        return factory;
    }

    @Bean
    public PlatformTransactionManager sharedTransactionManager(
            @Qualifier("sharedEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
