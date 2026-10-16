package com.gkcontas.multitenancy.tenant;

import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Creates and migrates the schema of one tenant. Schema per tenant moves
 * migrations from a build-time concern to a runtime one: the set of schemas is
 * not known when the jar is built, each schema needs its own
 * {@code flyway_schema_history}, and onboarding a tenant means running the
 * whole history against an empty schema while the application serves the
 * others.
 */
@Component
public class TenantProvisioner {

    public static final String TENANT_MIGRATIONS = "classpath:db/migration/tenant";

    private final DataSource dataSource;
    private final TenantProperties properties;
    private final JdbcTemplate jdbc;

    public TenantProvisioner(DataSource dataSource, TenantProperties properties) {
        this.dataSource = dataSource;
        this.properties = properties;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /**
     * Idempotent: creating a schema that exists is a no-op and Flyway skips
     * migrations already recorded in that schema's own history table.
     */
    public MigrateResult provision(String tenantId) {
        // Through the API the identifier is already constrained by a pattern,
        // but this is the method a scheduled job or an admin tool would call,
        // and the value ends up as an identifier in DDL either way.
        String schema = SchemaNames.require(properties.schemaFor(tenantId));
        return flywayFor(schema).migrate();
    }

    public List<MigrateResult> provisionAll(List<String> tenantIds) {
        return tenantIds.stream().map(this::provision).toList();
    }

    public void register(String tenantId, String displayName) {
        SchemaNames.require(properties.schemaFor(tenantId));
        jdbc.update("""
                insert into shared.tenant_registry (tenant_id, schema_name, display_name)
                values (?, ?, ?)
                on conflict (tenant_id) do update set display_name = excluded.display_name
                """, tenantId, properties.schemaFor(tenantId), displayName);
    }

    /**
     * Counts the relations the strategy costs in the catalog. Every tenant
     * schema carries a full copy of the model — tables, sequences, indexes and
     * constraints — and {@code pg_class} grows with the product of tenants and
     * objects.
     */
    public long tenantRelations() {
        Long count = jdbc.queryForObject("""
                select count(*)
                  from pg_class c
                  join pg_namespace n on n.oid = c.relnamespace
                 where n.nspname like ?
                """, Long.class, properties.getSchemaPrefix() + "%");
        return count == null ? 0 : count;
    }

    public long sharedRelations() {
        Long count = jdbc.queryForObject("""
                select count(*)
                  from pg_class c
                  join pg_namespace n on n.oid = c.relnamespace
                 where n.nspname = 'shared'
                """, Long.class);
        return count == null ? 0 : count;
    }

    private Flyway flywayFor(String schema) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .locations(TENANT_MIGRATIONS)
                .load();
    }
}
