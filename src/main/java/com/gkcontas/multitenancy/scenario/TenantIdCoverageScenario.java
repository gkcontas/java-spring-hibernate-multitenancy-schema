package com.gkcontas.multitenancy.scenario;

import com.gkcontas.multitenancy.billing.InvoiceStatus;
import com.gkcontas.multitenancy.measure.Probe;
import com.gkcontas.multitenancy.measure.ProbeOptions;
import com.gkcontas.multitenancy.measure.Probed;
import com.gkcontas.multitenancy.measure.ScenarioReport;
import com.gkcontas.multitenancy.measure.Variant;
import com.gkcontas.multitenancy.measure.VariantKind;
import com.gkcontas.multitenancy.shared.model.SharedCustomer;
import com.gkcontas.multitenancy.shared.repository.SharedCustomerRepository;
import com.gkcontas.multitenancy.shared.repository.SharedInvoiceRepository;
import com.gkcontas.multitenancy.tenant.TenantContext;
import jakarta.persistence.EntityManagerFactory;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * What does {@code @TenantId} actually cover?
 *
 * <p>Hibernate's discriminator support is a rewrite of the queries Hibernate
 * itself writes, plus a value it fills on insert. Everything on that list is
 * solid. Everything off it — and native SQL is off it — reaches the database
 * exactly as written.
 *
 * <p>Every variant runs as acme. The sizes differ per tenant on purpose, so a
 * missing predicate shows up as a larger number and not as a stack trace.
 */
@Component
public class TenantIdCoverageScenario implements Scenario {

    private static final String ACTOR = "acme";
    private static final String OTHER = "globex";

    private final Probe probe;
    private final TransactionTemplate transactions;
    private final SharedCustomerRepository customers;
    private final SharedInvoiceRepository invoices;
    private final EntityManagerFactory sharedUnit;

    public TenantIdCoverageScenario(Probe probe,
                                    @Qualifier("sharedTransactions") TransactionTemplate transactions,
                                    SharedCustomerRepository customers,
                                    SharedInvoiceRepository invoices,
                                    @Qualifier("sharedEntityManagerFactory")
                                    EntityManagerFactory sharedUnit) {
        this.probe = probe;
        this.transactions = transactions;
        this.customers = customers;
        this.invoices = invoices;
        this.sharedUnit = sharedUnit;
    }

    @Override
    public String name() {
        return "tenant-id-coverage";
    }

    @Override
    public String question() {
        return "Running as acme over the shared table: which of these see only acme's rows "
                + "- an HQL count, findById of another tenant's row, the same count in "
                + "native SQL, a bulk HQL update, and an insert that never sets the column?";
    }

    @Override
    public ScenarioReport run(ProbeOptions options) {
        // Second-level cache off the measurement path: a cached entity would
        // make a load look free and say nothing about the predicate.
        sharedUnit.getCache().evictAll();

        long otherTenantId = TenantContext.callAs(OTHER,
                () -> customers.idsOfNative(OTHER).getFirst());

        List<Variant> variants = new ArrayList<>();
        variants.add(hqlCount(options));
        variants.add(findByIdOfAnotherTenant(options, otherTenantId));
        variants.add(nativeCount(options));
        variants.add(bulkUpdate());
        variants.add(insertWithoutTenantId());
        variants.add(updateOfTheTenantColumn(otherTenantId));

        return ScenarioReport.of(name(), question(),
                "Everything Hibernate writes is filtered and everything it only forwards is "
                        + "not. The column also cannot be moved once it is written, which is "
                        + "the right default and the reason a tenant migration needs SQL.",
                variants);
    }

    private Variant hqlCount(ProbeOptions options) {
        Probed<Long> probed = probe.measure(options, () -> TenantContext.callAs(ACTOR,
                () -> transactions.execute(status -> customers.countForCurrentTenant())));
        return Variant.from("hql-count", VariantKind.FIX, probed)
                .outcome(probed.value() + " customers, and the SQL carries where tenant_id=?")
                .rows(probed.value())
                .build();
    }

    private Variant findByIdOfAnotherTenant(ProbeOptions options, long id) {
        Probed<Optional<SharedCustomer>> probed = probe.measure(options, () ->
                TenantContext.callAs(ACTOR,
                        () -> transactions.execute(status -> customers.findById(id))));
        return Variant.from("find-by-id-other-tenant", VariantKind.FIX, probed)
                .outcome("globex customer " + id + " loaded as acme -> "
                        + (probed.value().isEmpty() ? "empty" : "FOUND"))
                .rows(probed.value().isEmpty() ? 0L : 1L)
                .build();
    }

    private Variant nativeCount(ProbeOptions options) {
        Probed<Long> probed = probe.measure(options, () -> TenantContext.callAs(ACTOR,
                () -> transactions.execute(status -> customers.countAcrossAllTenantsNative())));
        return Variant.from("native-count", VariantKind.PROBLEM, probed)
                .outcome(probed.value() + " customers: every tenant in the table")
                .rows(probed.value())
                .build();
    }

    /**
     * Rolled back: the point is the row count the statement claims, not a
     * dataset with half its invoices restated.
     */
    private Variant bulkUpdate() {
        Probed<int[]> probed = probe.measureOnce(() -> TenantContext.callAs(ACTOR, () ->
                transactions.execute(status -> {
                    int affected = invoices.bulkRestatus(
                            InvoiceStatus.OVERDUE, InvoiceStatus.CANCELLED);
                    long mine = invoices.countByStatusHql(InvoiceStatus.CANCELLED);
                    status.setRollbackOnly();
                    return new int[]{affected, (int) mine};
                })));
        long affected = probed.value()[0];
        boolean filtered = probed.sql().stream()
                .filter(statement -> statement.sql().startsWith("update"))
                .anyMatch(statement -> whereClauseOf(statement.sql()).contains("tenant_id"));
        return Variant.from("bulk-hql-update", VariantKind.FIX, probed)
                .outcome(affected + " rows updated, and the generated update "
                        + (filtered ? "carries" : "does NOT carry") + " the tenant predicate")
                .rows(affected)
                .build();
    }

    private Variant insertWithoutTenantId() {
        Probed<String> probed = probe.measureOnce(() -> TenantContext.callAs(ACTOR, () ->
                transactions.execute(status -> {
                    SharedCustomer customer = new SharedCustomer();
                    customer.setName("Coverage Probe");
                    customer.setEmail("coverage-probe@example.com");
                    customer.setCreatedAt(OffsetDateTime.now());
                    SharedCustomer saved = customers.saveAndFlush(customer);
                    String written = saved.getTenantId();
                    status.setRollbackOnly();
                    return written;
                })));
        return Variant.from("insert-without-tenant-id", VariantKind.FIX, probed)
                .outcome("tenant_id written as " + probed.value()
                        + ", taken from the resolver and not from the caller")
                .rows(1L)
                .build();
    }

    /** Between {@code set} and {@code where}: the columns the update assigns. */
    private static String setClauseOf(String sql) {
        int set = sql.indexOf(" set ");
        int where = sql.indexOf(" where ");
        if (set < 0) {
            return "";
        }
        return where > set ? sql.substring(set, where) : sql.substring(set);
    }

    /** After {@code where}: the rows the statement claims to be about. */
    private static String whereClauseOf(String sql) {
        int where = sql.indexOf(" where ");
        return where < 0 ? "" : sql.substring(where);
    }

    private Variant updateOfTheTenantColumn(long otherId) {
        Probed<String> probed = probe.measureOnce(() -> TenantContext.callAs(ACTOR, () ->
                transactions.execute(status -> {
                    SharedCustomer mine = customers.findAll().getFirst();
                    mine.setTenantId(OTHER);
                    mine.setName(mine.getName() + " (touched)");
                    customers.saveAndFlush(mine);
                    status.setRollbackOnly();
                    return mine.getId() + " -> " + mine.getTenantId();
                })));
        boolean updatedTenant = probed.sql().stream()
                .filter(statement -> statement.sql().startsWith("update"))
                .anyMatch(statement -> setClauseOf(statement.sql()).contains("tenant_id"));
        return Variant.from("move-row-to-another-tenant", VariantKind.TRAP, probed)
                .outcome("tenant_id " + (updatedTenant ? "IS" : "is not")
                        + " in the generated update: the field is mapped immutable, so the "
                        + "assignment is kept in memory and never sent")
                .rows(1L)
                .build();
    }
}
