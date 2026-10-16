package com.gkcontas.multitenancy.scenario;

import com.gkcontas.multitenancy.billing.InvoiceStatus;
import com.gkcontas.multitenancy.measure.Probe;
import com.gkcontas.multitenancy.measure.ProbeOptions;
import com.gkcontas.multitenancy.measure.Probed;
import com.gkcontas.multitenancy.measure.ScenarioReport;
import com.gkcontas.multitenancy.measure.Variant;
import com.gkcontas.multitenancy.measure.VariantKind;
import com.gkcontas.multitenancy.schema.repository.InvoiceRepository;
import com.gkcontas.multitenancy.shared.repository.SharedInvoiceRepository;
import com.gkcontas.multitenancy.tenant.SchemaNames;
import com.gkcontas.multitenancy.tenant.TenantCatalog;
import com.gkcontas.multitenancy.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The paid total of every tenant, in one report.
 *
 * <p>This is the question schema per tenant is bad at, and the one the
 * discriminator column answers with a {@code group by}. The isolation that
 * makes a tenant query cheap is the same isolation that makes a question about
 * all of them N questions — and N is the customer list, so the report gets
 * slower every time somebody signs up.
 *
 * <p>All three variants run through a persistence unit, so the statement count
 * in the report is counted by the same instrument.
 */
@Component
public class CrossTenantReportScenario implements Scenario {

    private static final InvoiceStatus STATUS = InvoiceStatus.PAID;

    private final Probe probe;
    private final TenantCatalog catalog;
    private final InvoiceRepository schemaInvoices;
    private final SharedInvoiceRepository sharedInvoices;
    private final TransactionTemplate schemaTransactions;
    private final TransactionTemplate sharedTransactions;
    private final EntityManagerFactory sharedUnit;

    public CrossTenantReportScenario(Probe probe,
                                     TenantCatalog catalog,
                                     InvoiceRepository schemaInvoices,
                                     SharedInvoiceRepository sharedInvoices,
                                     @Qualifier("schemaTransactions")
                                     TransactionTemplate schemaTransactions,
                                     @Qualifier("sharedTransactions")
                                     TransactionTemplate sharedTransactions,
                                     @Qualifier("sharedEntityManagerFactory")
                                     EntityManagerFactory sharedUnit) {
        this.probe = probe;
        this.catalog = catalog;
        this.schemaInvoices = schemaInvoices;
        this.sharedInvoices = sharedInvoices;
        this.schemaTransactions = schemaTransactions;
        this.sharedTransactions = sharedTransactions;
        this.sharedUnit = sharedUnit;
    }

    @Override
    public String name() {
        return "cross-tenant-report";
    }

    @Override
    public String question() {
        return "The paid total of every tenant: one session per schema, one generated "
                + "union over every schema, and one group by over the shared table.";
    }

    @Override
    public ScenarioReport run(ProbeOptions options) {
        List<String> tenants = catalog.tenantIds();
        List<String> schemas = catalog.schemaNames().stream()
                .map(SchemaNames::require).toList();

        Variant loop = schemaLoop(options, tenants);
        Variant union = generatedUnion(options, schemas);
        Variant groupBy = discriminatorGroupBy(options);

        return ScenarioReport.of(name(), question(),
                "Over " + tenants.size() + " tenants the loop costs one session and one "
                        + "statement each, and both numbers are the customer list. The union "
                        + "brings it back to one statement at the price of SQL generated from "
                        + "the registry - a statement that grows with the tenant count and "
                        + "has to be rebuilt every time it changes. The discriminator column "
                        + "answers with a group by that does not care how many there are.",
                List.of(loop, union, groupBy));
    }

    /** One session per tenant, because one connection sees one schema. */
    private Variant schemaLoop(ProbeOptions options, List<String> tenants) {
        Probed<Map<String, Long>> probed = probe.measure(options, () -> {
            Map<String, Long> totals = new LinkedHashMap<>();
            for (String tenantId : tenants) {
                totals.put(tenantId, TenantContext.callAs(tenantId, () ->
                        schemaTransactions.execute(status ->
                                schemaInvoices.totalCentsByStatus(STATUS))));
            }
            return totals;
        });
        return Variant.from("schema-loop", VariantKind.COST, probed)
                .outcome(tenants.size() + " sessions, one statement each")
                .rows((long) probed.value().size())
                .build();
    }

    /**
     * One statement, built from the registry. It is a real answer and it is also
     * the strategy admitting that the schemas are one table after all.
     */
    private Variant generatedUnion(ProbeOptions options, List<String> schemas) {
        String sql = schemas.stream()
                .map(schema -> "select '" + schema + "' as schema_name,"
                        + " coalesce(sum(amount_cents), 0) as total from " + schema
                        + ".invoice where status = :status")
                .collect(Collectors.joining(" union all "));

        Probed<Integer> probed = probe.measure(options, () ->
                sharedTransactions.execute(status -> {
                    EntityManager em = sharedUnit.createEntityManager();
                    try {
                        return em.createNativeQuery(sql)
                                .setParameter("status", STATUS.name())
                                .getResultList().size();
                    } finally {
                        em.close();
                    }
                }));
        return Variant.from("generated-union-all", VariantKind.FIX, probed)
                .outcome("1 statement, " + schemas.size() + " branches, "
                        + sql.length() + " characters of generated SQL")
                .rows(Long.valueOf(probed.value()))
                .build();
    }

    private Variant discriminatorGroupBy(ProbeOptions options) {
        Probed<Integer> probed = probe.measure(options, () ->
                sharedTransactions.execute(status -> sharedInvoices.totalsPerTenant().size()));
        return Variant.from("discriminator-group-by", VariantKind.FIX, probed)
                .outcome("1 statement, one group per tenant, independent of the tenant count")
                .rows(Long.valueOf(probed.value()))
                .build();
    }
}
