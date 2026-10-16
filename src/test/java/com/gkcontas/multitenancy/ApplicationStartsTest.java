package com.gkcontas.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;

import com.gkcontas.multitenancy.tenant.TenantCatalog;
import com.gkcontas.multitenancy.tenant.TenantContext;
import com.gkcontas.multitenancy.schema.repository.CustomerRepository;
import com.gkcontas.multitenancy.shared.repository.SharedCustomerRepository;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

class ApplicationStartsTest extends IntegrationTestBase {

    @Autowired
    @Qualifier("schemaEntityManagerFactory")
    private EntityManagerFactory schemaUnit;

    @Autowired
    @Qualifier("sharedEntityManagerFactory")
    private EntityManagerFactory sharedUnit;

    @Autowired
    private TenantCatalog catalog;

    @Autowired
    private CustomerRepository schemaCustomers;

    @Autowired
    private SharedCustomerRepository sharedCustomers;

    @Test
    void bringsUpBothPersistenceUnits() {
        assertThat(schemaUnit).isNotNull();
        assertThat(sharedUnit).isNotNull();
        assertThat(schemaUnit).isNotSameAs(sharedUnit);
    }

    @Test
    void registersTheNamedTenants() {
        assertThat(catalog.tenantIds()).contains("acme", "globex", "initech");
        assertThat(catalog.schemaFor("acme")).isEqualTo("tenant_acme");
    }

    @Test
    void readsBothModelsUnderATenant() {
        TenantContext.runAs("acme", () -> {
            assertThat(schemaCustomers.count()).isNotNegative();
            assertThat(sharedCustomers.count()).isNotNegative();
        });
    }
}
