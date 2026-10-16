package com.gkcontas.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;

import com.gkcontas.multitenancy.tenant.TenantCatalog;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The two models have to hold the same data, or every comparison between them
 * is a comparison of datasets.
 */
class DatasetConsistencyTest extends ScenarioTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TenantCatalog catalog;

    @Test
    void everyRegisteredTenantHasItsSchema() {
        List<String> schemas = jdbc.queryForList(
                "select schema_name from information_schema.schemata"
                        + " where schema_name like 'tenant_%'", String.class);
        assertThat(schemas).containsAll(catalog.schemaNames());
    }

    @Test
    void eachTenantHasTheSameCustomerCountInBothModels() {
        catalog.tenantIds().forEach(tenantId -> {
            long inSchema = count("select count(*) from "
                    + catalog.schemaFor(tenantId) + ".customer");
            long inShared = jdbc.queryForObject(
                    "select count(*) from shared.customer where tenant_id = ?",
                    Long.class, tenantId);
            assertThat(inSchema).as(tenantId).isEqualTo(inShared);
        });
    }

    @Test
    void eachTenantHasTheSameInvoiceCountInAllThreeTables() {
        catalog.tenantIds().forEach(tenantId -> {
            long inSchema = count("select count(*) from "
                    + catalog.schemaFor(tenantId) + ".invoice");
            long inShared = jdbc.queryForObject(
                    "select count(*) from shared.invoice where tenant_id = ?",
                    Long.class, tenantId);
            long inNaive = jdbc.queryForObject(
                    "select count(*) from shared.invoice_naive where tenant_id = ?",
                    Long.class, tenantId);
            assertThat(inSchema).as(tenantId).isEqualTo(inShared).isEqualTo(inNaive);
        });
    }

    @Test
    void theTenantsHaveDifferentSizesSoALeakShowsUpAsAWrongNumber() {
        List<Long> counts = catalog.tenantIds().stream()
                .map(tenantId -> count("select count(*) from "
                        + catalog.schemaFor(tenantId) + ".customer"))
                .distinct()
                .toList();
        assertThat(counts).hasSizeGreaterThan(1);
    }

    @Test
    void noInvoicePointsAtAnotherTenantsCustomerInTheGuardedTable() {
        assertThat(count("""
                select count(*) from shared.invoice i
                 where not exists (select 1 from shared.customer c
                                    where c.id = i.customer_id
                                      and c.tenant_id = i.tenant_id)
                """)).isZero();
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }
}
