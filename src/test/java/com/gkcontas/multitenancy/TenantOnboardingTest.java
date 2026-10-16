package com.gkcontas.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gkcontas.multitenancy.schema.model.Customer;
import com.gkcontas.multitenancy.schema.repository.CustomerRepository;
import com.gkcontas.multitenancy.tenant.TenantCatalog;
import com.gkcontas.multitenancy.tenant.TenantContext;
import com.gkcontas.multitenancy.tenant.TenantProvisioner;
import com.gkcontas.multitenancy.tenant.UnknownTenantException;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Onboarding is the operation schema per tenant turns from a deploy into a
 * runtime feature. It has to work while the other tenants are being served,
 * and it has to be safe to repeat.
 */
class TenantOnboardingTest extends ScenarioTestBase {

    private static final String NEW_TENANT = "newcomer";

    @Autowired
    private TenantProvisioner provisioner;

    @Autowired
    private TenantCatalog catalog;

    @Autowired
    private CustomerRepository customers;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    @Qualifier("schemaTransactions")
    private TransactionTemplate transactions;

    @AfterEach
    void removeTheTenant() {
        jdbc.execute("drop schema if exists tenant_newcomer cascade");
        jdbc.update("delete from shared.tenant_registry where tenant_id = ?", NEW_TENANT);
    }

    @Test
    void createsTheSchemaAndRunsTheHistoryAgainstIt() {
        provisioner.provision(NEW_TENANT);

        assertThat(jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = ?",
                Long.class, "tenant_newcomer")).isEqualTo(3);
    }

    /**
     * Its own history table, and in it the schema-creation marker Flyway writes
     * so it knows the schema was empty when it got there - that row is what
     * makes dropping the tenant again a complete operation.
     */
    @Test
    void keepsItsOwnMigrationHistory() {
        provisioner.provision(NEW_TENANT);

        assertThat(jdbc.queryForObject(
                "select count(*) from tenant_newcomer.flyway_schema_history"
                        + " where success and version = '1'", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from tenant_newcomer.flyway_schema_history"
                        + " where success and version is null", Long.class)).isEqualTo(1);
    }

    @Test
    void isSafeToRunTwice() {
        provisioner.provision(NEW_TENANT);
        assertThat(provisioner.provision(NEW_TENANT).migrationsExecuted).isZero();
    }

    @Test
    void servesTheNewTenantAsSoonAsItIsRegistered() {
        provisioner.provision(NEW_TENANT);
        provisioner.register(NEW_TENANT, "Newcomer Ltd");

        assertThat(catalog.exists(NEW_TENANT)).isTrue();
        assertThat(TenantContext.callAs(NEW_TENANT, () -> customers.count())).isZero();

        Long id = TenantContext.callAs(NEW_TENANT, () -> transactions.execute(status -> {
            Customer customer = new Customer();
            customer.setName("First Customer");
            customer.setEmail("first@newcomer.example.com");
            customer.setCreatedAt(OffsetDateTime.now());
            return customers.save(customer).getId();
        }));

        assertThat(id).isEqualTo(1);
        assertThat(TenantContext.callAs(NEW_TENANT, () -> customers.count())).isEqualTo(1);
    }

    @Test
    void doesNotDisturbTheTenantsAlreadyBeingServed() {
        long before = TenantContext.callAs("acme", () -> customers.count());
        provisioner.provision(NEW_TENANT);
        provisioner.register(NEW_TENANT, "Newcomer Ltd");
        assertThat(TenantContext.callAs("acme", () -> customers.count())).isEqualTo(before);
    }

    @Test
    void refusesAnIdentifierThatIsNotRegistered() {
        assertThatThrownBy(() -> catalog.requireKnown("nobody"))
                .isInstanceOf(UnknownTenantException.class)
                .hasMessageContaining("nobody");
    }

    @Test
    void refusesAnIdentifierThatWouldNotBeASafeSchemaName() {
        assertThatThrownBy(() -> provisioner.provision("acme; drop schema shared cascade"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
