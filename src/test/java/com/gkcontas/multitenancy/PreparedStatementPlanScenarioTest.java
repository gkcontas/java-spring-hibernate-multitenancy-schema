package com.gkcontas.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;

import com.gkcontas.multitenancy.measure.ScenarioReport;
import org.junit.jupiter.api.Test;

/**
 * The scenario exists to settle a claim rather than to confirm one, so the
 * tests assert what was measured: PostgreSQL replans a cached plan when
 * {@code search_path} moves, and every answer came from the current schema.
 */
class PreparedStatementPlanScenarioTest extends ScenarioTestBase {

    @Test
    void reallyPromotesTheStatementToAServerSidePrepare() {
        ScenarioReport report = run("prepared-statement-plan");
        assertThat(variant(report, "server-side-prepare").outcome())
                .contains("1 statement(s) prepared server-side");
    }

    @Test
    void neverAnswersFromTheSchemaItWasPlannedAgainst() {
        ScenarioReport report = run("prepared-statement-plan");
        assertThat(variant(report, "server-side-prepare").rows()).isZero();
    }

    @Test
    void preparesNothingOnTheServerWithTheThresholdOff() {
        ScenarioReport report = run("prepared-statement-plan");
        assertThat(variant(report, "prepare-threshold-off").outcome())
                .contains("0 statement(s) prepared server-side");
    }

    @Test
    void givesEachTenantItsOwnStatementWhenTheSchemaIsInTheSql() {
        ScenarioReport report = run("prepared-statement-plan");
        assertThat(variant(report, "schema-qualified-sql").outcome())
                .contains("2 statement(s) prepared server-side");
        assertThat(variant(report, "schema-qualified-sql").rows()).isZero();
    }

    @Test
    void reportsTheExpectedCountsItComparedAgainst() {
        ScenarioReport report = run("prepared-statement-plan");
        assertThat(report.verdict()).contains("acme=40").contains("globex=15");
    }
}
