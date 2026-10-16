package com.gkcontas.multitenancy.scenario;

import com.gkcontas.multitenancy.measure.ProbeOptions;
import com.gkcontas.multitenancy.measure.ScenarioReport;
import com.gkcontas.multitenancy.measure.Variant;
import com.gkcontas.multitenancy.measure.VariantKind;
import com.gkcontas.multitenancy.tenant.SchemaNames;
import com.gkcontas.multitenancy.tenant.TenantCatalog;
import com.gkcontas.multitenancy.tenant.TenantProperties;
import com.gkcontas.multitenancy.tenant.TenantProvisioner;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * What the number of tenants costs outside query time.
 *
 * <p>Schema per tenant moves the schema from one thing the deploy owns to N
 * things, each with its own {@code flyway_schema_history}. Nothing about a
 * migration gets harder; it just happens N times, in a window that grows with
 * the customer list, and a failure halfway leaves the tenants in two different
 * versions at once.
 *
 * <p>The catalog is the other half of the bill, and the one nobody budgets for:
 * every schema carries a full copy of the model's tables, sequences, indexes
 * and constraints, and {@code pg_class} grows with the product.
 */
@Component
public class MigrationCatalogScenario implements Scenario {

    private static final String PROBE_TENANT = "onboardprobe";

    private final JdbcTemplate jdbc;
    private final DataSource dataSource;
    private final TenantProvisioner provisioner;
    private final TenantCatalog catalog;
    private final TenantProperties properties;

    public MigrationCatalogScenario(DataSource dataSource,
                                    TenantProvisioner provisioner,
                                    TenantCatalog catalog,
                                    TenantProperties properties) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.dataSource = dataSource;
        this.provisioner = provisioner;
        this.catalog = catalog;
        this.properties = properties;
    }

    @Override
    public String name() {
        return "migrations-and-catalog";
    }

    @Override
    public String question() {
        return "Onboarding one tenant, sweeping every tenant schema, migrating the shared "
                + "schema once, and the relations each model leaves in the catalog.";
    }

    @Override
    public ScenarioReport run(ProbeOptions options) {
        List<String> tenants = catalog.tenantIds();

        Variant onboard = onboardOneTenant();
        Variant sweep = sweepEveryTenantSchema(tenants);
        Variant shared = migrateSharedOnce();
        Variant catalogCost = catalogCost(tenants.size());

        return ScenarioReport.of(name(), question(),
                "The per-deploy cost of the schema strategy is one Flyway run per tenant, "
                        + "and it is linear and visible. The cost nobody plans for is the "
                        + "catalog: " + provisioner.tenantRelations() + " relations across "
                        + tenants.size() + " schemas against " + provisioner.sharedRelations()
                        + " for the discriminator model, and every one of them is a row other "
                        + "sessions read.",
                List.of(onboard, sweep, shared, catalogCost));
    }

    /** A brand-new schema taken from nothing to the current version, then dropped. */
    private Variant onboardOneTenant() {
        String schema = SchemaNames.require(properties.schemaFor(PROBE_TENANT));
        jdbc.execute("drop schema if exists " + schema + " cascade");

        long startedAt = System.nanoTime();
        MigrateResult result = provisioner.provision(PROBE_TENANT);
        double elapsedMs = (System.nanoTime() - startedAt) / 1_000_000.0;

        jdbc.execute("drop schema if exists " + schema + " cascade");

        return Variant.builder()
                .name("onboard-one-tenant")
                .kind(VariantKind.COST)
                .outcome("create schema plus " + result.migrationsExecuted
                        + " migration(s) against an empty schema, while the application "
                        + "serves the others")
                .rows((long) result.migrationsExecuted)
                .medianMs(round(elapsedMs))
                .build();
    }

    /** The deploy: every tenant schema checked, and only the new scripts run. */
    private Variant sweepEveryTenantSchema(List<String> tenants) {
        long startedAt = System.nanoTime();
        List<MigrateResult> results = provisioner.provisionAll(tenants);
        double elapsedMs = (System.nanoTime() - startedAt) / 1_000_000.0;

        int executed = results.stream().mapToInt(result -> result.migrationsExecuted).sum();
        return Variant.builder()
                .name("sweep-every-tenant-schema")
                .kind(VariantKind.COST)
                .outcome(tenants.size() + " schemas visited, " + executed
                        + " migration(s) executed - all already at the current version")
                .rows((long) tenants.size())
                .medianMs(round(elapsedMs))
                .build();
    }

    /** The same work for the discriminator model: one schema, once, forever. */
    private Variant migrateSharedOnce() {
        long startedAt = System.nanoTime();
        MigrateResult result = Flyway.configure()
                .dataSource(dataSource)
                .schemas("shared")
                .defaultSchema("shared")
                .locations("classpath:db/migration/shared")
                .load()
                .migrate();
        double elapsedMs = (System.nanoTime() - startedAt) / 1_000_000.0;

        return Variant.builder()
                .name("migrate-shared-once")
                .kind(VariantKind.FIX)
                .outcome("1 schema visited, " + result.migrationsExecuted
                        + " migration(s) executed - the number does not depend on the "
                        + "tenant count")
                .rows(1L)
                .medianMs(round(elapsedMs))
                .build();
    }

    private Variant catalogCost(int tenants) {
        long tenantRelations = provisioner.tenantRelations();
        long sharedRelations = provisioner.sharedRelations();
        return Variant.builder()
                .name("catalog-relations")
                .kind(VariantKind.COST)
                .outcome(tenantRelations + " relations in " + tenants
                        + " tenant schemas against " + sharedRelations + " in shared, "
                        + (tenants == 0 ? "n/a" : (tenantRelations / Math.max(tenants, 1))
                                + " per tenant"))
                .rows(tenantRelations)
                .build();
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
