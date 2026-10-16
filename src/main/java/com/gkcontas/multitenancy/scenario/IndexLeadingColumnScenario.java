package com.gkcontas.multitenancy.scenario;

import com.gkcontas.multitenancy.measure.ProbeOptions;
import com.gkcontas.multitenancy.measure.ScenarioReport;
import com.gkcontas.multitenancy.measure.SqlStatement;
import com.gkcontas.multitenancy.measure.Variant;
import com.gkcontas.multitenancy.measure.VariantKind;
import com.gkcontas.multitenancy.tenant.SchemaNames;
import com.gkcontas.multitenancy.tenant.TenantProperties;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * One tenant's overdue invoices, over a table that holds every tenant's.
 *
 * <p>This one is not about Hibernate at all — the predicate is the same in all
 * three variants and {@code @TenantId} puts it there for free. It is about what
 * the index does with it. An index on {@code (status, issued_on)} is perfectly
 * usable and perfectly wrong: PostgreSQL walks every tenant's entries for that
 * status and throws away the ones that are not acme's, which is the number the
 * plan reports as rows removed by filter.
 *
 * <p>Measured through plain JDBC, so the SQL in the report is the SQL that was
 * planned, and with {@code EXPLAIN (ANALYZE, BUFFERS)} alongside the timing.
 */
@Component
public class IndexLeadingColumnScenario implements Scenario {

    private static final String ACTOR = "acme";
    private static final String STATUS = "OVERDUE";

    private final JdbcTemplate jdbc;
    private final TenantProperties properties;

    public IndexLeadingColumnScenario(DataSource dataSource, TenantProperties properties) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.properties = properties;
    }

    @Override
    public String name() {
        return "index-leading-column";
    }

    @Override
    public String question() {
        return "The overdue invoices of one tenant: over an index on (status, issued_on), "
                + "over an index on (tenant_id, status, issued_on), and over the tenant's "
                + "own schema.";
    }

    @Override
    public ScenarioReport run(ProbeOptions options) {
        String tenantSchema = SchemaNames.require(properties.schemaFor(ACTOR));

        List<Variant> variants = List.of(
                measure("naive-index", VariantKind.PROBLEM,
                        "select id, number, amount_cents from shared.invoice_naive"
                                + " where tenant_id = ? and status = ? order by issued_on desc",
                        options, ACTOR, STATUS),
                measure("tenant-leading-index", VariantKind.FIX,
                        "select id, number, amount_cents from shared.invoice"
                                + " where tenant_id = ? and status = ? order by issued_on desc",
                        options, ACTOR, STATUS),
                measure("schema-per-tenant", VariantKind.FIX,
                        "select id, number, amount_cents from " + tenantSchema + ".invoice"
                                + " where status = ? order by issued_on desc",
                        options, STATUS));

        return ScenarioReport.of(name(), question(),
                "The discriminator has to lead the index of every tenant-scoped query, and "
                        + "there is one per query shape. In the tenant's own schema the index "
                        + "is tenant-scoped because the table is - the same two columns, and "
                        + "nothing left to get wrong.",
                variants);
    }

    private Variant measure(String name, VariantKind kind, String sql,
                            ProbeOptions options, Object... args) {
        for (int i = 0; i < options.warmups(); i++) {
            jdbc.query(sql, rows -> {
            }, args);
        }

        List<Double> times = new ArrayList<>(options.repeats());
        long rows = 0;
        for (int i = 0; i < options.repeats(); i++) {
            long startedAt = System.nanoTime();
            rows = jdbc.query(sql, resultSet -> {
                long counted = 0;
                while (resultSet.next()) {
                    counted++;
                }
                return counted;
            }, args);
            times.add((System.nanoTime() - startedAt) / 1_000_000.0);
        }

        return Variant.builder()
                .name(name)
                .kind(kind)
                .outcome(planSummary(sql, args))
                .statements(1L)
                .rows(rows)
                .medianMs(median(times))
                .sql(List.of(new SqlStatement(sql, 1)))
                .build();
    }

    /**
     * The three lines of the plan that answer the question: which index was
     * used, how much of it was read, and how much of what was read was thrown
     * away again.
     */
    private String planSummary(String sql, Object... args) {
        List<String> plan = jdbc.queryForList(
                "explain (analyze, buffers) " + sql, String.class, args);
        String node = plan.stream()
                .map(String::trim)
                .filter(line -> line.startsWith("->") || line.startsWith("Index")
                        || line.startsWith("Seq Scan") || line.startsWith("Bitmap"))
                .findFirst()
                .orElse("plan unavailable")
                .replaceFirst("^-> *", "")
                .replaceAll("\\s+\\(cost=.*", "");
        String removed = plan.stream()
                .map(String::trim)
                .filter(line -> line.startsWith("Rows Removed by Filter"))
                .findFirst()
                .orElse("Rows Removed by Filter: 0");
        String buffers = plan.stream()
                .map(String::trim)
                .filter(line -> line.startsWith("Buffers:"))
                .findFirst()
                .orElse("Buffers: n/a");
        return node + "; " + removed + "; " + buffers;
    }

    private static double median(List<Double> times) {
        List<Double> sorted = new ArrayList<>(times);
        sorted.sort(Double::compareTo);
        int middle = sorted.size() / 2;
        double value = sorted.size() % 2 == 1
                ? sorted.get(middle)
                : (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
        return Math.round(value * 100.0) / 100.0;
    }
}
