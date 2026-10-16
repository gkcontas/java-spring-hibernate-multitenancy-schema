package com.gkcontas.multitenancy.scenario;

import com.gkcontas.multitenancy.measure.ProbeOptions;
import com.gkcontas.multitenancy.measure.ScenarioReport;
import com.gkcontas.multitenancy.measure.Variant;
import com.gkcontas.multitenancy.measure.VariantKind;
import com.gkcontas.multitenancy.schema.model.Customer;
import com.gkcontas.multitenancy.schema.repository.CustomerRepository;
import com.gkcontas.multitenancy.tenant.SchemaNames;
import com.gkcontas.multitenancy.tenant.TenantContext;
import com.gkcontas.multitenancy.tenant.TenantNotResolvedException;
import com.gkcontas.multitenancy.tenant.TenantProperties;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Where does the row land when the work leaves the request thread?
 *
 * <p>The tenant lives in a thread local, which is right for a request handled
 * start to finish on one thread and silent about every hand-off. Each variant
 * creates the same customer for globex a different way, and then the scenario
 * asks the database which schema received it.
 *
 * <p>The number that matters here is not a time. It is a schema name.
 */
@Component
public class TenantResolutionScenario implements Scenario {

    private static final String OWNER = "globex";
    private static final String MARKER = "resolution-probe@example.com";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final CustomerRepository customers;
    private final TenantProperties properties;

    public TenantResolutionScenario(DataSource dataSource,
                                    @Qualifier("schemaTransactions") TransactionTemplate transactions,
                                    CustomerRepository customers,
                                    TenantProperties properties) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.transactions = transactions;
        this.customers = customers;
        this.properties = properties;
    }

    @Override
    public String name() {
        return "tenant-resolution";
    }

    @Override
    public String question() {
        return "A customer is created for globex. Which schema receives the row when the "
                + "work runs on the request thread, on a plain executor, on a propagating "
                + "task, and with no fallback tenant?";
    }

    @Override
    public ScenarioReport run(ProbeOptions options) {
        boolean fallbackWas = properties.isFallbackEnabled();
        List<Variant> variants = new ArrayList<>();
        try {
            properties.setFallbackEnabled(true);

            TenantContext.runAs(OWNER, this::insertCustomer);
            variants.add(landed("request-thread", VariantKind.FIX));

            TenantContext.runAs(OWNER, () -> submit(this::insertCustomer));
            variants.add(landed("plain-executor", VariantKind.PROBLEM));

            TenantContext.runAs(OWNER,
                    () -> submit(TenantContext.propagating(this::insertCustomer)));
            variants.add(landed("propagating-task", VariantKind.FIX));

            properties.setFallbackEnabled(false);
            variants.add(withoutFallback());
        } finally {
            properties.setFallbackEnabled(fallbackWas);
            clearMarkers();
        }

        return ScenarioReport.of(name(), question(),
                "A thread local plus a fallback tenant is not a default - it is a silent "
                        + "write into somebody else's schema, answered with 200. Without the "
                        + "fallback the same bug is an exception before the first statement.",
                variants);
    }

    private Variant withoutFallback() {
        String outcome;
        try {
            TenantContext.runAs(OWNER, () -> submit(this::insertCustomer));
            outcome = "no exception: a row was written somewhere";
        } catch (RuntimeException e) {
            outcome = resolutionFailure(e)
                    .map(failure -> failure.getClass().getSimpleName()
                            + " before the first statement")
                    .orElseGet(() -> e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return Variant.builder()
                .name("no-fallback")
                .kind(VariantKind.FIX)
                .outcome(outcome)
                .rows(countMarkers())
                .build();
    }

    private Variant landed(String name, VariantKind kind) {
        List<String> schemas = new ArrayList<>();
        long total = 0;
        for (String tenantId : properties.bootstrapIds()) {
            long count = countMarker(properties.schemaFor(tenantId));
            total += count;
            if (count > 0) {
                schemas.add(properties.schemaFor(tenantId));
            }
        }
        String expected = properties.schemaFor(OWNER);
        String outcome = schemas.isEmpty()
                ? "no row written"
                : "row landed in " + String.join(", ", schemas)
                        + (schemas.equals(List.of(expected)) ? " (the right one)"
                                : " (NOT " + expected + ")");
        clearMarkers();
        return Variant.builder()
                .name(name)
                .kind(kind)
                .outcome(outcome)
                .rows(total)
                .build();
    }

    /**
     * Written through the persistence unit, not through SQL: it is the session
     * that decides the schema, and the session asks the resolver on whichever
     * thread opens it.
     */
    private void insertCustomer() {
        transactions.executeWithoutResult(status -> {
            Customer customer = new Customer();
            customer.setName("Resolution Probe");
            customer.setEmail(MARKER);
            customer.setCreatedAt(OffsetDateTime.now());
            customers.save(customer);
        });
    }

    private void submit(Runnable task) {
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<?> future = executor.submit(task);
            future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(cause);
        }
    }

    private long countMarker(String tenantSchema) {
        Long count = jdbc.queryForObject(
                "select count(*) from " + SchemaNames.require(tenantSchema)
                        + ".customer where email = ?",
                Long.class, MARKER);
        return count == null ? 0 : count;
    }

    private long countMarkers() {
        return properties.bootstrapIds().stream()
                .mapToLong(tenantId -> countMarker(properties.schemaFor(tenantId)))
                .sum();
    }

    private void clearMarkers() {
        properties.bootstrapIds().forEach(tenantId -> jdbc.update(
                "delete from " + SchemaNames.require(properties.schemaFor(tenantId))
                        + ".customer where email = ?", MARKER));
    }

    private static java.util.Optional<Throwable> resolutionFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof TenantNotResolvedException) {
                return java.util.Optional.of(current);
            }
            current = current.getCause();
        }
        return java.util.Optional.empty();
    }
}
