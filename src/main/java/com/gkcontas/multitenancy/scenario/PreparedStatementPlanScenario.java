package com.gkcontas.multitenancy.scenario;

import com.gkcontas.multitenancy.measure.ProbeOptions;
import com.gkcontas.multitenancy.measure.ScenarioReport;
import com.gkcontas.multitenancy.measure.Variant;
import com.gkcontas.multitenancy.measure.VariantKind;
import com.gkcontas.multitenancy.tenant.SchemaNames;
import com.gkcontas.multitenancy.tenant.TenantProperties;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The oldest warning about schema-per-tenant on a shared connection: a plan is
 * made once and {@code search_path} is resolved when it is made, so a
 * server-side prepared statement reused after the schema moved could keep
 * answering from the schema it was planned against.
 *
 * <p>The scenario does not assume either answer. It opens one connection with
 * {@code prepareThreshold=2} — so pgjdbc promotes the statement to a server-side
 * one after two executions — then alternates {@code search_path} between two
 * tenants whose row counts differ, runs the same unqualified SQL twelve times,
 * and prints the sequence of counts it got back.
 *
 * <p>Two more variants are run for comparison: the same loop with server-side
 * prepares turned off, and the same loop with the schema written into the SQL,
 * which gives each tenant its own statement text and therefore its own plan.
 */
@Component
public class PreparedStatementPlanScenario implements Scenario {

    private static final List<String> ACTORS = List.of("acme", "globex");
    private static final int ROUNDS = 12;

    private final JdbcTemplate jdbc;
    private final TenantProperties properties;
    private final String url;
    private final String username;
    private final String password;

    public PreparedStatementPlanScenario(DataSource dataSource,
                                         TenantProperties properties,
                                         @Value("${spring.datasource.url}") String url,
                                         @Value("${spring.datasource.username}") String username,
                                         @Value("${spring.datasource.password}") String password) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.properties = properties;
        this.url = url;
        this.username = username;
        this.password = password;
    }

    @Override
    public String name() {
        return "prepared-statement-plan";
    }

    @Override
    public String question() {
        return "One connection, two tenants, the same unqualified SQL twelve times with "
                + "search_path moved in between. Does a server-side prepared statement keep "
                + "answering from the schema it was planned against?";
    }

    @Override
    public ScenarioReport run(ProbeOptions options) {
        Map<String, Long> truth = new LinkedHashMap<>();
        ACTORS.forEach(tenant -> truth.put(tenant, countIn(properties.schemaFor(tenant))));

        Variant shared = loop("server-side-prepare", VariantKind.COST, 2, false, truth);
        List<Variant> variants = List.of(
                shared,
                loop("prepare-threshold-off", VariantKind.FIX, 0, false, truth),
                loop("schema-qualified-sql", VariantKind.FIX, 2, true, truth));

        String verdict = shared.rows() == 0
                ? "Expected row counts " + truth + ". The classic warning does not reproduce: "
                        + "PostgreSQL tracks search_path as a dependency of a cached plan and "
                        + "replans when it moves, so the server-side statement answered from "
                        + "the current schema every time. Writing the schema into the SQL is "
                        + "still the cheaper guarantee - it does not depend on that."
                : "Expected row counts " + truth + ". The server-side statement kept answering "
                        + "from the schema it was planned against in " + shared.rows() + " of "
                        + ROUNDS + " rounds.";

        return ScenarioReport.of(name(), question(), verdict, variants);
    }

    private Variant loop(String name, VariantKind kind, int prepareThreshold,
                         boolean qualified, Map<String, Long> truth) {
        List<String> observed = new ArrayList<>(ROUNDS);
        int wrong = 0;
        long serverSide = 0;
        long startedAt = System.nanoTime();

        Properties connectionProperties = new Properties();
        connectionProperties.setProperty("user", username);
        connectionProperties.setProperty("password", password);
        connectionProperties.setProperty("prepareThreshold", Integer.toString(prepareThreshold));

        try (Connection connection = DriverManager.getConnection(url, connectionProperties)) {
            for (int round = 0; round < ROUNDS; round++) {
                String tenant = ACTORS.get(round % ACTORS.size());
                String schema = SchemaNames.require(properties.schemaFor(tenant));

                try (Statement statement = connection.createStatement()) {
                    statement.execute("set search_path to " + schema);
                }

                String sql = qualified
                        ? "select count(*) from " + schema + ".customer"
                        : "select count(*) from customer";
                long count;
                try (PreparedStatement query = connection.prepareStatement(sql);
                     ResultSet rows = query.executeQuery()) {
                    rows.next();
                    count = rows.getLong(1);
                }

                observed.add(tenant + "=" + count);
                if (!truth.get(tenant).equals(count)) {
                    wrong++;
                }
            }
            // Evidence that the mechanism under test was engaged at all: a
            // server-side prepare shows up as a named statement on the session.
            serverSide = preparedOnServer(connection);
        } catch (SQLException e) {
            throw new IllegalStateException("prepared statement probe failed", e);
        }

        double elapsedMs = (System.nanoTime() - startedAt) / 1_000_000.0;
        return Variant.builder()
                .name(name)
                .kind(kind)
                .outcome(wrong + " of " + ROUNDS + " answers came from the wrong schema; "
                        + serverSide + " statement(s) prepared server-side; sequence "
                        + String.join(" ", observed.subList(0, 6)) + " ...")
                .statements((long) ROUNDS)
                .rows((long) wrong)
                .medianMs(Math.round(elapsedMs * 100.0) / 100.0)
                .build();
    }

    private static long preparedOnServer(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "select count(*) from pg_prepared_statements")) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private long countIn(String schema) {
        Long count = jdbc.queryForObject(
                "select count(*) from " + SchemaNames.require(schema) + ".customer", Long.class);
        return count == null ? 0 : count;
    }
}
