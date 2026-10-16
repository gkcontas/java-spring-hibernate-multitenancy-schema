package com.gkcontas.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;

import com.gkcontas.multitenancy.measure.ScenarioReport;
import com.gkcontas.multitenancy.measure.Variant;
import org.junit.jupiter.api.Test;

class IndexLeadingColumnScenarioTest extends ScenarioTestBase {

    @Test
    void everyVariantAnswersTheSameQuestion() {
        ScenarioReport report = run("index-leading-column");
        long rows = variant(report, "naive-index").rows();
        assertThat(rows).isPositive();
        assertThat(variant(report, "tenant-leading-index").rows()).isEqualTo(rows);
        assertThat(variant(report, "schema-per-tenant").rows()).isEqualTo(rows);
    }

    @Test
    void theNaiveIndexReadsRowsItThenThrowsAway() {
        Variant naive = variant(run("index-leading-column"), "naive-index");
        assertThat(naive.outcome()).doesNotContain("Rows Removed by Filter: 0");
    }

    @Test
    void theTenantLeadingIndexThrowsNothingAway() {
        Variant fixed = variant(run("index-leading-column"), "tenant-leading-index");
        assertThat(fixed.outcome())
                .contains("invoice_tenant_status_idx")
                .contains("Rows Removed by Filter: 0");
    }

    @Test
    void theTenantSchemaNeedsNoTenantColumnInItsIndex() {
        Variant own = variant(run("index-leading-column"), "schema-per-tenant");
        assertThat(own.outcome()).contains("Rows Removed by Filter: 0");
    }

    @Test
    void eachVariantIsOneStatement() {
        ScenarioReport report = run("index-leading-column");
        assertThat(report.variants()).allSatisfy(
                variant -> assertThat(variant.statements()).isEqualTo(1));
    }
}
