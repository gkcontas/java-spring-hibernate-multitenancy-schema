package com.gkcontas.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;

import com.gkcontas.multitenancy.measure.ScenarioReport;
import com.gkcontas.multitenancy.measure.VariantKind;
import org.junit.jupiter.api.Test;

class TenantResolutionScenarioTest extends ScenarioTestBase {

    @Test
    void writesToTheRightSchemaOnTheRequestThread() {
        ScenarioReport report = run("tenant-resolution");
        assertThat(variant(report, "request-thread").outcome()).contains("tenant_globex");
    }

    @Test
    void writesToTheFallbackSchemaWhenTheContextDoesNotReachTheWorkerThread() {
        ScenarioReport report = run("tenant-resolution");
        assertThat(variant(report, "plain-executor").outcome())
                .contains("tenant_acme")
                .contains("NOT tenant_globex");
        assertThat(variant(report, "plain-executor").rows()).isEqualTo(1);
    }

    @Test
    void writesToTheRightSchemaWhenTheTaskCarriesTheContext() {
        ScenarioReport report = run("tenant-resolution");
        assertThat(variant(report, "propagating-task").outcome())
                .contains("tenant_globex")
                .contains("the right one");
    }

    @Test
    void failsBeforeTheFirstStatementWhenThereIsNoFallback() {
        ScenarioReport report = run("tenant-resolution");
        assertThat(variant(report, "no-fallback").outcome())
                .contains("TenantNotResolvedException");
        assertThat(variant(report, "no-fallback").rows()).isZero();
    }

    @Test
    void namesTheExecutorVariantAsTheProblem() {
        ScenarioReport report = run("tenant-resolution");
        assertThat(variant(report, "plain-executor").kind()).isEqualTo(VariantKind.PROBLEM);
    }

    @Test
    void leavesNoProbeRowsBehind() {
        run("tenant-resolution");
        ScenarioReport again = run("tenant-resolution");
        assertThat(variant(again, "request-thread").rows()).isEqualTo(1);
    }
}
