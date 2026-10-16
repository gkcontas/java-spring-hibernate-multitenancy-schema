package com.gkcontas.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;

import com.gkcontas.multitenancy.measure.ScenarioReport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class MigrationCatalogScenarioTest extends ScenarioTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void onboardsABrandNewTenantByRunningTheWholeHistory() {
        ScenarioReport report = run("migrations-and-catalog");
        assertThat(variant(report, "onboard-one-tenant").rows()).isEqualTo(1);
        assertThat(variant(report, "onboard-one-tenant").medianMs()).isPositive();
    }

    @Test
    void dropsTheProbeSchemaAgain() {
        run("migrations-and-catalog");
        assertThat(jdbc.queryForObject(
                "select count(*) from information_schema.schemata where schema_name = ?",
                Long.class, "tenant_onboardprobe")).isZero();
    }

    @Test
    void visitsEverySchemaOnASweepAndExecutesNothingTwice() {
        ScenarioReport report = run("migrations-and-catalog");
        assertThat(variant(report, "sweep-every-tenant-schema").outcome())
                .contains("0 migration(s) executed");
        assertThat(variant(report, "sweep-every-tenant-schema").rows())
                .isEqualTo(registeredTenants());
    }

    @Test
    void migratesTheSharedSchemaOnceRegardlessOfTheTenantCount() {
        ScenarioReport report = run("migrations-and-catalog");
        assertThat(variant(report, "migrate-shared-once").rows()).isEqualTo(1);
    }

    @Test
    void chargesTheCatalogOncePerTenant() {
        ScenarioReport report = run("migrations-and-catalog");
        long tenantRelations = variant(report, "catalog-relations").rows();
        assertThat(tenantRelations).isGreaterThan(registeredTenants());
        assertThat(variant(report, "catalog-relations").outcome()).contains("per tenant");
    }

    private long registeredTenants() {
        Long count = jdbc.queryForObject(
                "select count(*) from shared.tenant_registry", Long.class);
        return count == null ? 0 : count;
    }
}
