package com.gkcontas.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;

import com.gkcontas.multitenancy.measure.ScenarioReport;
import com.gkcontas.multitenancy.measure.VariantKind;
import org.junit.jupiter.api.Test;

class TenantIdCoverageScenarioTest extends ScenarioTestBase {

    @Test
    void filtersTheQueriesHibernateWrites() {
        ScenarioReport report = run("tenant-id-coverage");
        assertThat(variant(report, "hql-count").rows()).isEqualTo(40);
        assertThat(variant(report, "hql-count").sql().getFirst().sql())
                .contains("tenant_id = ?");
    }

    @Test
    void addsThePredicateToALoadByIdentifier() {
        ScenarioReport report = run("tenant-id-coverage");
        assertThat(variant(report, "find-by-id-other-tenant").rows()).isZero();
        assertThat(variant(report, "find-by-id-other-tenant").sql().getFirst().sql())
                .contains("id=?")
                .contains("tenant_id = ?");
    }

    @Test
    void doesNotTouchNativeSql() {
        ScenarioReport report = run("tenant-id-coverage");
        assertThat(variant(report, "native-count").kind()).isEqualTo(VariantKind.PROBLEM);
        assertThat(variant(report, "native-count").rows())
                .isGreaterThan(variant(report, "hql-count").rows());
        assertThat(variant(report, "native-count").sql().getFirst().sql())
                .doesNotContain("tenant_id");
    }

    @Test
    void narrowsABulkUpdateToTheCurrentTenant() {
        ScenarioReport report = run("tenant-id-coverage");
        assertThat(variant(report, "bulk-hql-update").outcome())
                .contains("carries the tenant predicate");
        assertThat(variant(report, "bulk-hql-update").rows()).isEqualTo(60);
    }

    @Test
    void fillsTheColumnFromTheResolverOnInsert() {
        ScenarioReport report = run("tenant-id-coverage");
        assertThat(variant(report, "insert-without-tenant-id").outcome())
                .contains("tenant_id written as acme");
        assertThat(variant(report, "insert-without-tenant-id").sql().getFirst().sql())
                .contains("tenant_id");
    }

    @Test
    void refusesToMoveARowToAnotherTenant() {
        ScenarioReport report = run("tenant-id-coverage");
        assertThat(variant(report, "move-row-to-another-tenant").outcome())
                .contains("tenant_id is not in the generated update");
        assertThat(variant(report, "move-row-to-another-tenant").kind())
                .isEqualTo(VariantKind.TRAP);
    }
}
