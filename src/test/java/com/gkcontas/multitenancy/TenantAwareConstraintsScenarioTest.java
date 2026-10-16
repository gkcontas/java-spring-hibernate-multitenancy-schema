package com.gkcontas.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;

import com.gkcontas.multitenancy.measure.ScenarioReport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class TenantAwareConstraintsScenarioTest extends ScenarioTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void aGlobalUniqueKeyLetsOneTenantTakeAnothersInvoiceNumber() {
        ScenarioReport report = run("tenant-aware-constraints");
        assertThat(variant(report, "global-unique-number").outcome())
                .contains("rejected by invoice_naive_number_key");
    }

    @Test
    void aCompositeUniqueKeyLetsBothTenantsUseTheSameNumber() {
        ScenarioReport report = run("tenant-aware-constraints");
        assertThat(variant(report, "composite-unique-number").outcome()).contains("accepted");
    }

    @Test
    void aForeignKeyThatIgnoresTheTenantAcceptsAnothersCustomer() {
        ScenarioReport report = run("tenant-aware-constraints");
        assertThat(variant(report, "cross-tenant-foreign-key").outcome()).contains("accepted");
    }

    @Test
    void aCompositeForeignKeyRefusesIt() {
        ScenarioReport report = run("tenant-aware-constraints");
        assertThat(variant(report, "composite-foreign-key").outcome())
                .contains("rejected by invoice_customer_fk");
    }

    @Test
    void schemaPerTenantRefusesItWithoutATenantColumnAtAll() {
        ScenarioReport report = run("tenant-aware-constraints");
        assertThat(variant(report, "schema-per-tenant-foreign-key").outcome())
                .contains("rejected by invoice_customer_fk");
    }

    @Test
    void rollsEveryProbeBack() {
        run("tenant-aware-constraints");
        assertThat(jdbc.queryForObject(
                "select count(*) from shared.invoice where number in ('INV-1', 'FK-PROBE-1')",
                Long.class)).isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from shared.invoice_naive where number in"
                        + " ('INV-1', 'FK-PROBE-1')", Long.class)).isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from tenant_initech.invoice where number = 'FK-PROBE-2'",
                Long.class)).isZero();
    }
}
