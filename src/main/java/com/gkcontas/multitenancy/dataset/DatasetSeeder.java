package com.gkcontas.multitenancy.dataset;

import com.gkcontas.multitenancy.billing.InvoiceStatus;
import com.gkcontas.multitenancy.tenant.SchemaNames;
import com.gkcontas.multitenancy.tenant.TenantCatalog;
import com.gkcontas.multitenancy.tenant.TenantProperties;
import com.gkcontas.multitenancy.tenant.TenantProvisioner;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.sql.DataSource;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Fills both models with the same data, so a scenario that compares them
 * compares strategies and not datasets.
 *
 * <p>Everything here is written through plain JDBC with the schema spelled out
 * in the SQL. Seeding through the two persistence units would mean opening a
 * session per tenant and letting the resolver decide where the rows land, which
 * is the thing under test - not a good foundation for the fixture the test runs
 * against.
 *
 * <p>Invoice numbers are prefixed with the tenant id, which is what makes the
 * global unique key on {@code invoice_naive.number} survive seeding. Scenario
 * {@code tenant-aware-constraints} shows what the same key does the moment two
 * tenants number their own invoices.
 */
@Component
public class DatasetSeeder {

    private static final List<InvoiceStatus> STATUS_CYCLE = List.of(
            InvoiceStatus.DRAFT, InvoiceStatus.ISSUED, InvoiceStatus.ISSUED,
            InvoiceStatus.PAID, InvoiceStatus.PAID, InvoiceStatus.PAID,
            InvoiceStatus.OVERDUE, InvoiceStatus.CANCELLED);

    private final JdbcTemplate jdbc;
    private final TenantProperties properties;
    private final TenantProvisioner provisioner;
    private final TenantCatalog catalog;

    public DatasetSeeder(DataSource dataSource,
                         TenantProperties properties,
                         TenantProvisioner provisioner,
                         TenantCatalog catalog) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.properties = properties;
        this.provisioner = provisioner;
        this.catalog = catalog;
    }

    /** Named tenants only. Enough for every functional scenario. */
    public SeedReport seedNamed() {
        return seed(false);
    }

    /** Named plus bulk. Needed by whatever is measured against a tenant count. */
    public SeedReport seedAll() {
        return seed(true);
    }

    private SeedReport seed(boolean includeBulk) {
        long startedAt = System.nanoTime();

        List<TenantProperties.BootstrapTenant> plan =
                new ArrayList<>(properties.getBootstrap());
        if (includeBulk) {
            for (int i = 1; i <= properties.getBulkCount(); i++) {
                TenantProperties.BootstrapTenant bulk = new TenantProperties.BootstrapTenant();
                bulk.setId(String.format("bulk%02d", i));
                bulk.setDisplayName("Bulk Tenant " + i);
                bulk.setCustomers(properties.getBulkCustomers());
                bulk.setInvoicesPerCustomer(properties.getBulkInvoicesPerCustomer());
                plan.add(bulk);
            }
        }

        plan.forEach(tenant -> {
            provisioner.provision(tenant.getId());
            provisioner.register(tenant.getId(), tenant.getDisplayName());
        });

        truncateShared();
        catalog.schemaNames().forEach(this::truncateTenantSchema);

        int customers = 0;
        int invoices = 0;
        for (TenantProperties.BootstrapTenant tenant : plan) {
            customers += tenant.getCustomers();
            invoices += tenant.getCustomers() * tenant.getInvoicesPerCustomer();
            seedTenant(tenant);
        }

        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
        return new SeedReport(plan.size(), customers, invoices,
                provisioner.tenantRelations(), provisioner.sharedRelations(), elapsedMs);
    }

    private void seedTenant(TenantProperties.BootstrapTenant tenant) {
        String schema = SchemaNames.require(properties.schemaFor(tenant.getId()));
        Random random = new Random(tenant.getId().hashCode());

        List<Long> tenantCustomerIds = insertCustomers(
                schema + ".customer", null, tenant, random);
        List<Long> sharedCustomerIds = insertCustomers(
                "shared.customer", tenant.getId(), tenant, new Random(tenant.getId().hashCode()));

        insertInvoices(schema + ".invoice", null, tenant, tenantCustomerIds,
                new Random(tenant.getId().hashCode() + 1L));
        insertInvoices("shared.invoice", tenant.getId(), tenant, sharedCustomerIds,
                new Random(tenant.getId().hashCode() + 1L));
        insertInvoices("shared.invoice_naive", tenant.getId(), tenant, sharedCustomerIds,
                new Random(tenant.getId().hashCode() + 1L));
    }

    private List<Long> insertCustomers(String table, String tenantId,
                                       TenantProperties.BootstrapTenant tenant, Random random) {
        List<Long> ids = new ArrayList<>(tenant.getCustomers());
        String sql = tenantId == null
                ? "insert into " + table + " (name, email, created_at) values (?, ?, now())"
                        + " returning id"
                : "insert into " + table + " (tenant_id, name, email, created_at)"
                        + " values (?, ?, ?, now()) returning id";

        for (int i = 1; i <= tenant.getCustomers(); i++) {
            String name = tenant.getDisplayName() + " Customer " + i;
            String email = tenant.getId() + ".customer" + i + "@example.com";
            Long id = tenantId == null
                    ? jdbc.queryForObject(sql, Long.class, name, email)
                    : jdbc.queryForObject(sql, Long.class, tenantId, name, email);
            ids.add(id);
            random.nextInt();
        }
        return ids;
    }

    private void insertInvoices(String table, String tenantId,
                                TenantProperties.BootstrapTenant tenant,
                                List<Long> customerIds, Random random) {
        int perCustomer = tenant.getInvoicesPerCustomer();
        int total = customerIds.size() * perCustomer;

        List<Object[]> rows = new ArrayList<>(total);
        int sequence = 0;
        for (Long customerId : customerIds) {
            for (int i = 0; i < perCustomer; i++) {
                sequence++;
                rows.add(new Object[]{
                        customerId,
                        tenant.getId() + "-INV-" + sequence,
                        STATUS_CYCLE.get(sequence % STATUS_CYCLE.size()).name(),
                        1_000L + random.nextInt(900_000),
                        Date.valueOf(LocalDate.of(2024, 1, 1).plusDays(sequence % 700))
                });
            }
        }

        String sql = tenantId == null
                ? "insert into " + table
                        + " (customer_id, number, status, amount_cents, issued_on)"
                        + " values (?, ?, ?, ?, ?)"
                : "insert into " + table
                        + " (tenant_id, customer_id, number, status, amount_cents, issued_on)"
                        + " values (?, ?, ?, ?, ?, ?)";

        jdbc.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int index) throws java.sql.SQLException {
                Object[] row = rows.get(index);
                int column = 1;
                if (tenantId != null) {
                    ps.setString(column++, tenantId);
                }
                ps.setLong(column++, (Long) row[0]);
                ps.setString(column++, (String) row[1]);
                ps.setString(column++, (String) row[2]);
                ps.setLong(column++, (Long) row[3]);
                ps.setDate(column, (Date) row[4]);
            }

            @Override
            public int getBatchSize() {
                return rows.size();
            }
        });
    }

    private void truncateShared() {
        jdbc.execute("truncate table shared.invoice, shared.invoice_naive, shared.customer"
                + " restart identity cascade");
    }

    private void truncateTenantSchema(String rawSchema) {
        String schema = SchemaNames.require(rawSchema);
        jdbc.execute("truncate table " + schema + ".invoice, " + schema + ".customer"
                + " restart identity cascade");
    }
}
