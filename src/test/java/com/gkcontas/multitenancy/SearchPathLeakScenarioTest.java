package com.gkcontas.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;

import com.gkcontas.multitenancy.measure.ScenarioReport;
import com.gkcontas.multitenancy.tenant.TenantProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SearchPathLeakScenarioTest extends ScenarioTestBase {

    @Autowired
    private TenantProperties properties;

    @Test
    void leavesTheTenantSchemaOnAPooledConnectionWithoutTheReset() {
        ScenarioReport report = run("search-path-leak");
        assertThat(variant(report, "no-reset").rows()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void anUnqualifiedQueryOnALeakedConnectionReturnsTheOtherTenantsRows() {
        ScenarioReport report = run("search-path-leak");
        assertThat(variant(report, "no-reset").outcome()).contains("40 rows");
    }

    @Test
    void leavesNothingBehindWithTheReset() {
        ScenarioReport report = run("search-path-leak");
        assertThat(variant(report, "reset-on-release").rows()).isZero();
    }

    @Test
    void anUnqualifiedQueryOnACleanConnectionFailsLoudly() {
        ScenarioReport report = run("search-path-leak");
        assertThat(variant(report, "reset-on-release").outcome())
                .contains("relation \"customer\" does not exist");
    }

    @Test
    void putsTheFlagBackWhereItFoundIt() {
        run("search-path-leak");
        assertThat(properties.isResetSearchPathOnRelease()).isTrue();
    }
}
