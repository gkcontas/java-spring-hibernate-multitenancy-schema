package com.gkcontas.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;

import com.gkcontas.multitenancy.measure.ScenarioReport;
import org.junit.jupiter.api.Test;

class CacheKeyScenarioTest extends ScenarioTestBase {

    @Test
    void aTenantBlindKeyServesOneTenantsTotalToAnother() {
        ScenarioReport report = run("cache-key");
        assertThat(variant(report, "tenant-blind-key").outcome())
                .contains("globex was served acme's total");
        assertThat(variant(report, "tenant-blind-key").rows()).isEqualTo(1);
    }

    @Test
    void theTenantInTheKeyFixesIt() {
        ScenarioReport report = run("cache-key");
        assertThat(variant(report, "tenant-in-key").outcome()).contains("both right");
        assertThat(variant(report, "tenant-in-key").rows()).isZero();
    }

    @Test
    void hibernatesOwnCacheAlreadyKeysByTheTenant() {
        ScenarioReport report = run("cache-key");
        assertThat(variant(report, "hibernate-entity-cache").outcome())
                .contains("cache hit(s)")
                .contains("as globex -> empty");
        assertThat(variant(report, "hibernate-entity-cache").rows()).isZero();
    }

    @Test
    void reportsBothRealTotalsSoTheWrongAnswerIsReadable() {
        ScenarioReport report = run("cache-key");
        assertThat(report.verdict()).containsPattern("acme's paid total is \\d+");
        assertThat(report.verdict()).containsPattern("globex's is \\d+");
    }
}
