package com.gkcontas.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;

import com.gkcontas.multitenancy.measure.ScenarioReport;
import com.gkcontas.multitenancy.measure.VariantKind;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class CrossTenantReportScenarioTest extends ScenarioTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void theSchemaLoopCostsOneStatementPerTenant() {
        ScenarioReport report = run("cross-tenant-report");
        assertThat(variant(report, "schema-loop").statements()).isEqualTo(tenants());
    }

    @Test
    void theGeneratedUnionIsOneStatement() {
        ScenarioReport report = run("cross-tenant-report");
        assertThat(variant(report, "generated-union-all").statements()).isEqualTo(1);
        assertThat(variant(report, "generated-union-all").outcome())
                .contains(tenants() + " branches");
    }

    @Test
    void theGroupByIsOneStatementThatDoesNotGrow() {
        ScenarioReport report = run("cross-tenant-report");
        assertThat(variant(report, "discriminator-group-by").statements()).isEqualTo(1);
        assertThat(variant(report, "discriminator-group-by").outcome())
                .doesNotContain("branches");
    }

    @Test
    void allThreeCoverTheSameTenants() {
        ScenarioReport report = run("cross-tenant-report");
        assertThat(report.variants()).allSatisfy(
                variant -> assertThat(variant.rows()).isEqualTo(tenants()));
    }

    @Test
    void namesTheLoopAsThePriceOfTheStrategyRatherThanABug() {
        ScenarioReport report = run("cross-tenant-report");
        assertThat(variant(report, "schema-loop").kind()).isEqualTo(VariantKind.COST);
    }

    private long tenants() {
        Long count = jdbc.queryForObject(
                "select count(*) from shared.tenant_registry", Long.class);
        return count == null ? 0 : count;
    }
}
