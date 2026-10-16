package com.gkcontas.multitenancy.scenario;

import com.gkcontas.multitenancy.measure.ProbeOptions;
import com.gkcontas.multitenancy.measure.ScenarioReport;
import com.gkcontas.multitenancy.measure.Variant;
import com.gkcontas.multitenancy.measure.VariantKind;
import com.gkcontas.multitenancy.tenant.SchemaNames;
import com.gkcontas.multitenancy.tenant.TenantProperties;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/**
 * Which of two tenants can stop the other?
 *
 * <p>{@code @TenantId} rewrites queries. It does not write DDL, and a
 * constraint that does not mention the discriminator is a constraint shared by
 * every tenant. Two of them are worth the trouble of measuring: a unique key
 * lets one tenant take a value out of another tenant's namespace, and a foreign
 * key that only names {@code customer (id)} is satisfied by any tenant's
 * customer.
 *
 * <p>Each variant runs in a transaction of its own and rolls it back, through
 * plain JDBC. The subject is the database's answer, and going through the
 * persistence unit would mean Hibernate answering first — on its own terms, and
 * only for the paths it controls.
 */
@Component
public class TenantAwareConstraintsScenario implements Scenario {

    private static final String ONE = "acme";
    private static final String TWO = "globex";
    private static final String CONTESTED_NUMBER = "INV-1";

    private final DataSource dataSource;
    private final TenantProperties properties;

    public TenantAwareConstraintsScenario(DataSource dataSource, TenantProperties properties) {
        this.dataSource = dataSource;
        this.properties = properties;
    }

    @Override
    public String name() {
        return "tenant-aware-constraints";
    }

    @Override
    public String question() {
        return "Two tenants both number an invoice INV-1, and one of them offers the other's "
                + "customer id. Which table says no, and to whom?";
    }

    @Override
    public ScenarioReport run(ProbeOptions options) {
        long oneCustomerId = customerOf(ONE);
        long otherCustomerId = customerOf(TWO);

        List<Variant> variants = List.of(
                sameNumberTwice("global-unique-number", VariantKind.PROBLEM,
                        "shared.invoice_naive", oneCustomerId, otherCustomerId),
                sameNumberTwice("composite-unique-number", VariantKind.FIX,
                        "shared.invoice", oneCustomerId, otherCustomerId),
                foreignCustomer("cross-tenant-foreign-key", VariantKind.PROBLEM,
                        "shared.invoice_naive", otherCustomerId),
                foreignCustomer("composite-foreign-key", VariantKind.FIX,
                        "shared.invoice", otherCustomerId),
                schemaPerTenantForeignKey());

        return ScenarioReport.of(name(), question(),
                "In the discriminator model the tenant has to lead every unique key and take "
                        + "part in every foreign key, and the second one needs a redundant "
                        + "unique (tenant_id, id) on the parent to point at. Schema per tenant "
                        + "gets both for nothing: there is no identifier that names another "
                        + "tenant's row.",
                variants);
    }

    /**
     * Each tenant offers one of its own customers, so the only thing left for
     * either table to object to is the invoice number.
     */
    private Variant sameNumberTwice(String name, VariantKind kind, String table,
                                    long oneCustomerId, long twoCustomerId) {
        String outcome = inRolledBackTransaction(connection -> {
            insertInvoice(connection, table, ONE, oneCustomerId, CONTESTED_NUMBER);
            insertInvoice(connection, table, TWO, twoCustomerId, CONTESTED_NUMBER);
        });
        return Variant.builder()
                .name(name)
                .kind(kind)
                .outcome(table + ": acme takes " + CONTESTED_NUMBER + ", then globex -> "
                        + outcome)
                .statements(2L)
                .build();
    }

    private Variant foreignCustomer(String name, VariantKind kind, String table, long foreignId) {
        String outcome = inRolledBackTransaction(connection ->
                insertInvoice(connection, table, ONE, foreignId, "FK-PROBE-1"));
        return Variant.builder()
                .name(name)
                .kind(kind)
                .outcome(table + ": acme invoice pointing at globex customer " + foreignId
                        + " -> " + outcome)
                .statements(1L)
                .build();
    }

    /**
     * The same attempt against the schema-per-tenant model. There is no tenant
     * column to get wrong; the identifier offered simply is not in this
     * schema's {@code customer} table.
     */
    private Variant schemaPerTenantForeignKey() {
        String schema = SchemaNames.require(properties.schemaFor("initech"));
        long beyondRange = scalar("select max(id) + 1 from " + schema + ".customer");
        String outcome = inRolledBackTransaction(connection -> {
            try (PreparedStatement insert = connection.prepareStatement(
                    "insert into " + schema + ".invoice"
                            + " (customer_id, number, status, amount_cents, issued_on)"
                            + " values (?, ?, 'ISSUED', 1000, current_date)")) {
                insert.setLong(1, beyondRange);
                insert.setString(2, "FK-PROBE-2");
                insert.executeUpdate();
            }
        });
        return Variant.builder()
                .name("schema-per-tenant-foreign-key")
                .kind(VariantKind.FIX)
                .outcome(schema + ": invoice pointing at customer " + beyondRange
                        + ", which exists in another tenant's schema -> " + outcome)
                .statements(1L)
                .build();
    }

    private void insertInvoice(Connection connection, String table, String tenantId,
                               long customerId, String number) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "insert into " + table
                        + " (tenant_id, customer_id, number, status, amount_cents, issued_on)"
                        + " values (?, ?, ?, 'ISSUED', 1000, current_date)")) {
            insert.setString(1, tenantId);
            insert.setLong(2, customerId);
            insert.setString(3, number);
            insert.executeUpdate();
        }
    }

    private String inRolledBackTransaction(ThrowingConsumer work) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute("set search_path to pg_catalog");
            }
            try {
                work.accept(connection);
                return "accepted";
            } catch (SQLException e) {
                return constraintOf(e);
            } finally {
                connection.rollback();
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("constraint probe failed", e);
        }
    }

    /** The constraint name is the evidence; the message around it is noise. */
    private static String constraintOf(SQLException e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        for (String candidate : List.of("invoice_naive_number_key", "invoice_tenant_number_key",
                "invoice_naive_customer_fk", "invoice_customer_fk", "invoice_number_key")) {
            if (message.contains(candidate)) {
                return "rejected by " + candidate;
            }
        }
        return "rejected: " + message.lines().findFirst().orElse(message);
    }

    private long customerOf(String tenantId) {
        return scalar("select id from shared.customer where tenant_id = ? order by id limit 1",
                tenantId);
    }

    private long scalar(String sql, Object... args) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement query = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                query.setObject(i + 1, args[i]);
            }
            try (ResultSet rows = query.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("could not read " + sql, e);
        }
    }

    @FunctionalInterface
    private interface ThrowingConsumer {

        void accept(Connection connection) throws SQLException;
    }
}
