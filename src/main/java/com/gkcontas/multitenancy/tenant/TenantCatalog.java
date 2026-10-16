package com.gkcontas.multitenancy.tenant;

import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The registry of tenants, read from the shared schema. Every query here names
 * {@code shared.} explicitly: this template borrows from the same pool the
 * tenant connections come from, so an unqualified table name would resolve
 * against whatever {@code search_path} the previous borrower left behind.
 */
@Component
public class TenantCatalog {

    private final JdbcTemplate jdbc;
    private final TenantProperties properties;

    public TenantCatalog(DataSource dataSource, TenantProperties properties) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.properties = properties;
    }

    public List<String> tenantIds() {
        return jdbc.queryForList(
                "select tenant_id from shared.tenant_registry order by tenant_id", String.class);
    }

    public List<String> schemaNames() {
        return jdbc.queryForList(
                "select schema_name from shared.tenant_registry order by tenant_id", String.class);
    }

    public boolean exists(String tenantId) {
        Long count = jdbc.queryForObject(
                "select count(*) from shared.tenant_registry where tenant_id = ?",
                Long.class, tenantId);
        return count != null && count > 0;
    }

    public void requireKnown(String tenantId) {
        if (!exists(tenantId)) {
            throw new UnknownTenantException(tenantId);
        }
    }

    public String schemaFor(String tenantId) {
        return properties.schemaFor(tenantId);
    }
}
