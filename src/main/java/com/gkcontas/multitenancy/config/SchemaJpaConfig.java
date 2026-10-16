package com.gkcontas.multitenancy.config;

import com.gkcontas.multitenancy.measure.StatementRecorder;
import com.gkcontas.multitenancy.tenant.SchemaPerTenantConnectionProvider;
import com.gkcontas.multitenancy.tenant.TenantIdentifierResolver;
import com.gkcontas.multitenancy.tenant.TenantProperties;
import com.gkcontas.multitenancy.tenant.TenantProvisioner;
import jakarta.persistence.EntityManagerFactory;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.MultiTenancySettings;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * The schema-per-tenant persistence unit.
 *
 * <p>Multi-tenancy is a property of the {@code SessionFactory}, not of a query
 * or of an entity, so the two strategies this project compares cannot share
 * one. That is why the application wires two persistence units by hand instead
 * of letting Spring Boot auto-configure one — and it is also the honest shape
 * of the trade-off: an application that needs both pays for both.
 *
 * <p>Both units sit on the same {@link DataSource}. One pool, one process, one
 * set of tenants.
 */
@Configuration
@EnableConfigurationProperties(TenantProperties.class)
@EnableJpaRepositories(
        basePackages = "com.gkcontas.multitenancy.schema.repository",
        entityManagerFactoryRef = "schemaEntityManagerFactory",
        transactionManagerRef = "schemaTransactionManager")
public class SchemaJpaConfig {

    @Bean
    public TenantIdentifierResolver tenantIdentifierResolver(TenantProperties properties) {
        return new TenantIdentifierResolver(properties);
    }

    @Bean
    public SchemaPerTenantConnectionProvider schemaPerTenantConnectionProvider(
            DataSource dataSource, TenantProperties properties) {
        String validationSchema = properties.schemaFor(properties.bootstrapIds().getFirst());
        return new SchemaPerTenantConnectionProvider(dataSource, properties, validationSchema);
    }

    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean schemaEntityManagerFactory(
            DataSource dataSource,
            TenantProperties properties,
            TenantProvisioner provisioner,
            SchemaPerTenantConnectionProvider connectionProvider,
            TenantIdentifierResolver tenantIdentifierResolver) {

        // The tenant schemas have to exist before this unit bootstraps, because
        // ddl-auto: validate reads a real schema through getAnyConnection().
        // Schema per tenant turns migrations into a startup dependency.
        provisioner.provisionAll(properties.bootstrapIds());

        Map<String, Object> hibernate = new HashMap<>(CommonHibernateProperties.base());
        hibernate.put(MultiTenancySettings.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
        hibernate.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantIdentifierResolver);
        // Only the one schema getAnyConnection() points at is validated. There
        // is no connection that sees all of them, so "the schema is valid" is a
        // statement about one tenant - the price of the strategy, not a bug.
        hibernate.put(AvailableSettings.HBM2DDL_AUTO, "validate");

        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan("com.gkcontas.multitenancy.schema.model");
        factory.setPersistenceUnitName("schema-per-tenant");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(hibernate);
        return factory;
    }

    @Bean
    @Primary
    public PlatformTransactionManager schemaTransactionManager(
            @org.springframework.beans.factory.annotation.Qualifier("schemaEntityManagerFactory")
            EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    /** Shared by both persistence units, so the two are measured alike. */
    static final class CommonHibernateProperties {

        private CommonHibernateProperties() {
        }

        static Map<String, Object> base() {
            Map<String, Object> properties = new HashMap<>();
            properties.put(AvailableSettings.GENERATE_STATISTICS, true);
            properties.put(AvailableSettings.STATEMENT_INSPECTOR,
                    StatementRecorder.class.getName());
            properties.put(AvailableSettings.PHYSICAL_NAMING_STRATEGY,
                    "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
            properties.put(AvailableSettings.IMPLICIT_NAMING_STRATEGY,
                    "org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy");
            return properties;
        }
    }
}
