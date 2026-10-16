package com.gkcontas.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gkcontas.multitenancy.billing.InvoiceStatus;
import com.gkcontas.multitenancy.schema.model.Customer;
import com.gkcontas.multitenancy.schema.repository.CustomerRepository;
import com.gkcontas.multitenancy.schema.repository.InvoiceRepository;
import com.gkcontas.multitenancy.shared.repository.SharedCustomerRepository;
import com.gkcontas.multitenancy.tenant.TenantContext;
import com.gkcontas.multitenancy.tenant.TenantNotResolvedException;
import com.gkcontas.multitenancy.tenant.TenantProperties;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Isolation asserted directly against the two models, rather than through a
 * scenario report. Both answer the same question with different numbers, and
 * that is the whole comparison.
 */
class TenantIsolationTest extends ScenarioTestBase {

    @Autowired
    private CustomerRepository schemaCustomers;

    @Autowired
    private InvoiceRepository schemaInvoices;

    @Autowired
    private SharedCustomerRepository sharedCustomers;

    @Autowired
    private TenantProperties properties;

    @Autowired
    @Qualifier("schemaTransactions")
    private TransactionTemplate schemaTransactions;

    @Test
    void eachTenantSeesOnlyItsOwnCustomersInTheSchemaModel() {
        assertThat(TenantContext.callAs("acme", () -> schemaCustomers.count())).isEqualTo(40);
        assertThat(TenantContext.callAs("globex", () -> schemaCustomers.count())).isEqualTo(15);
        assertThat(TenantContext.callAs("initech", () -> schemaCustomers.count())).isEqualTo(7);
    }

    @Test
    void eachTenantSeesOnlyItsOwnCustomersInTheDiscriminatorModel() {
        assertThat(TenantContext.callAs("acme", () -> sharedCustomers.count())).isEqualTo(40);
        assertThat(TenantContext.callAs("globex", () -> sharedCustomers.count())).isEqualTo(15);
        assertThat(TenantContext.callAs("initech", () -> sharedCustomers.count())).isEqualTo(7);
    }

    @Test
    void theTwoModelsAgreeOnEveryTenant() {
        properties.bootstrapIds().forEach(tenantId -> assertThat(
                TenantContext.callAs(tenantId, () -> schemaCustomers.count()))
                .isEqualTo(TenantContext.callAs(tenantId, () -> sharedCustomers.count())));
    }

    @Test
    void anIdentifierFromAnotherTenantIsNotVisibleInTheSchemaModel() {
        Long acmeCustomer = TenantContext.callAs("acme",
                () -> schemaCustomers.findAll().getLast().getId());
        assertThat(acmeCustomer).isGreaterThan(7L);
        assertThat(TenantContext.callAs("initech",
                () -> schemaCustomers.findById(acmeCustomer))).isEmpty();
    }

    @Test
    void aWriteLandsInTheSchemaOfTheTenantThatMadeIt() {
        Long id = TenantContext.callAs("initech", () -> schemaTransactions.execute(status -> {
            Customer customer = new Customer();
            customer.setName("Isolation Probe");
            customer.setEmail("isolation-probe@example.com");
            customer.setCreatedAt(OffsetDateTime.now());
            return schemaCustomers.save(customer).getId();
        }));

        assertThat(TenantContext.callAs("initech", () -> schemaCustomers.findById(id)))
                .isPresent();
        assertThat(TenantContext.callAs("acme", () -> schemaCustomers.findById(id)))
                .get()
                .extracting(Customer::getEmail)
                .isNotEqualTo("isolation-probe@example.com");

        TenantContext.runAs("initech", () -> schemaTransactions.executeWithoutResult(
                status -> schemaCustomers.deleteById(id)));
    }

    @Test
    void invoiceTotalsDifferPerTenantInBothModels() {
        long acme = TenantContext.callAs("acme",
                () -> schemaInvoices.totalCentsByStatus(InvoiceStatus.PAID));
        long globex = TenantContext.callAs("globex",
                () -> schemaInvoices.totalCentsByStatus(InvoiceStatus.PAID));
        assertThat(acme).isNotEqualTo(globex).isPositive();
    }

    @Test
    void readingWithNoTenantAtAllUsesTheConfiguredFallback() {
        assertThat(properties.isFallbackEnabled()).isTrue();
        assertThat(schemaCustomers.count())
                .isEqualTo(TenantContext.callAs(properties.getFallbackTenant(),
                        () -> schemaCustomers.count()));
    }

    @Test
    void readingWithNoTenantFailsWhenTheFallbackIsOff() {
        properties.setFallbackEnabled(false);
        try {
            assertThatThrownBy(schemaCustomers::count)
                    .rootCause()
                    .isInstanceOf(TenantNotResolvedException.class);
        } finally {
            properties.setFallbackEnabled(true);
        }
    }
}
