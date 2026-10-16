package com.gkcontas.multitenancy.scenario;

import com.gkcontas.multitenancy.measure.ProbeOptions;
import com.gkcontas.multitenancy.measure.ScenarioReport;
import com.gkcontas.multitenancy.measure.Variant;
import com.gkcontas.multitenancy.measure.VariantKind;
import com.gkcontas.multitenancy.schema.repository.InvoiceRepository;
import com.gkcontas.multitenancy.tenant.TenantContext;
import com.gkcontas.multitenancy.tenant.TenantProperties;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * What does the next borrower of the connection see?
 *
 * <p>A pooled connection is not a new connection. {@code search_path} is
 * session state and it survives the return to the pool, so the tenant a
 * connection was pointed at outlives the transaction that pointed it there
 * unless something undoes it.
 *
 * <p>The scenario borrows every connection in the pool afterwards and reads its
 * {@code search_path}, which turns "could leak" into a count. Then it runs an
 * unqualified {@code select count(*) from customer} on each one, which is what
 * any {@code JdbcTemplate}, health check or report in the same application
 * would be doing.
 */
@Component
public class SearchPathLeakScenario implements Scenario {

    private static final String ACTOR = "acme";

    private final DataSource dataSource;
    private final TransactionTemplate transactions;
    private final InvoiceRepository invoices;
    private final TenantProperties properties;

    public SearchPathLeakScenario(DataSource dataSource,
                                  @Qualifier("schemaTransactions") TransactionTemplate transactions,
                                  InvoiceRepository invoices,
                                  TenantProperties properties) {
        this.dataSource = dataSource;
        this.transactions = transactions;
        this.invoices = invoices;
        this.properties = properties;
    }

    @Override
    public String name() {
        return "search-path-leak";
    }

    @Override
    public String question() {
        return "After one transaction for acme, how many of the pooled connections still "
                + "point at tenant_acme, and what does an unqualified query on them return?";
    }

    @Override
    public ScenarioReport run(ProbeOptions options) {
        boolean resetWas = properties.isResetSearchPathOnRelease();
        List<Variant> variants = new ArrayList<>();
        try {
            properties.setResetSearchPathOnRelease(false);
            readAsTenant();
            variants.add(inspectPool("no-reset", VariantKind.PROBLEM));

            scrubPool();

            properties.setResetSearchPathOnRelease(true);
            readAsTenant();
            variants.add(inspectPool("reset-on-release", VariantKind.FIX));
        } finally {
            properties.setResetSearchPathOnRelease(resetWas);
            scrubPool();
        }

        return ScenarioReport.of(name(), question(),
                "The connection has to be cleaned on the way back, and pg_catalog is a "
                        + "better neutral value than public: an unqualified table name then "
                        + "fails loudly instead of resolving to a schema that happens to be "
                        + "there.",
                variants);
    }

    /** One ordinary read, enough to make the provider point a connection at a tenant. */
    private void readAsTenant() {
        TenantContext.runAs(ACTOR, () ->
                transactions.executeWithoutResult(status -> invoices.countAll()));
    }

    private Variant inspectPool(String name, VariantKind kind) {
        int poolSize = poolSize();
        List<Connection> borrowed = new ArrayList<>(poolSize);
        int carryingTenantSchema = 0;
        String unqualifiedOutcome = "not reached";
        try {
            for (int i = 0; i < poolSize; i++) {
                borrowed.add(dataSource.getConnection());
            }
            for (Connection connection : borrowed) {
                String searchPath = searchPath(connection);
                if (searchPath.contains(properties.getSchemaPrefix())) {
                    carryingTenantSchema++;
                    if ("not reached".equals(unqualifiedOutcome)) {
                        unqualifiedOutcome = unqualifiedCount(connection);
                    }
                }
            }
            if (carryingTenantSchema == 0) {
                unqualifiedOutcome = unqualifiedCount(borrowed.getFirst());
            }
        } catch (SQLException e) {
            throw new IllegalStateException("could not inspect the pool", e);
        } finally {
            borrowed.forEach(connection -> {
                try {
                    connection.close();
                } catch (SQLException ignored) {
                    // Nothing useful to do: the pool will discard it.
                }
            });
        }

        return Variant.builder()
                .name(name)
                .kind(kind)
                .outcome(carryingTenantSchema + " of " + poolSize
                        + " pooled connections point at a tenant schema; "
                        + "select count(*) from customer -> " + unqualifiedOutcome)
                .rows((long) carryingTenantSchema)
                .build();
    }

    private String unqualifiedCount(Connection connection) {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("select count(*) from customer")) {
            rows.next();
            return rows.getLong(1) + " rows";
        } catch (SQLException e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName()
                    : e.getMessage().lines().findFirst().orElse(e.getMessage());
            return "SQLException: " + message;
        }
    }

    private String searchPath(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("show search_path")) {
            rows.next();
            return rows.getString(1);
        }
    }

    /**
     * Cleanup between variants: without it the connections the previous variant
     * dirtied would still be dirty, and the next measurement would be about
     * them rather than about the flag.
     */
    private void scrubPool() {
        int poolSize = poolSize();
        List<Connection> borrowed = new ArrayList<>(poolSize);
        try {
            for (int i = 0; i < poolSize; i++) {
                Connection connection = dataSource.getConnection();
                borrowed.add(connection);
                try (Statement statement = connection.createStatement()) {
                    statement.execute("set search_path to pg_catalog");
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("could not scrub the pool", e);
        } finally {
            borrowed.forEach(connection -> {
                try {
                    connection.close();
                } catch (SQLException ignored) {
                    // See above.
                }
            });
        }
    }

    private int poolSize() {
        if (dataSource instanceof HikariDataSource hikari) {
            return hikari.getMaximumPoolSize();
        }
        return 1;
    }
}
