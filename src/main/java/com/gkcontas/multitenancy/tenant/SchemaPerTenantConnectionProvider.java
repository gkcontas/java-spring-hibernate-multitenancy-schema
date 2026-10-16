package com.gkcontas.multitenancy.tenant;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;

/**
 * Schema per tenant on a single connection pool. Hibernate asks for a
 * connection and names the tenant; the provider takes any connection from the
 * pool and points its {@code search_path} at that tenant's schema.
 *
 * <p>Two details decide whether this is safe.
 *
 * <p>The first is the reset on release. A pooled connection is not a new
 * connection: {@code search_path} is session state and it survives the return
 * to the pool. Without the reset, the next borrower — Hibernate on behalf of
 * another tenant, a {@code JdbcTemplate}, a health check, Flyway — inherits
 * the schema of whoever used it last. The flag exists so scenario
 * {@code search-path-leak} can count how many connections in the pool carry a
 * foreign schema.
 *
 * <p>The second is the schema name. It goes into SQL by concatenation, because
 * {@code SET search_path} takes an identifier and not a bind parameter, so the
 * name is validated against a strict pattern before it is ever used.
 */
public class SchemaPerTenantConnectionProvider implements MultiTenantConnectionProvider<String> {

    private static final String NEUTRAL_SEARCH_PATH = "pg_catalog";

    private final transient DataSource dataSource;
    private final TenantProperties properties;
    private final String validationSchema;

    private final AtomicLong searchPathSwitches = new AtomicLong();
    private final AtomicLong searchPathResets = new AtomicLong();

    public SchemaPerTenantConnectionProvider(DataSource dataSource,
                                             TenantProperties properties,
                                             String validationSchema) {
        this.dataSource = dataSource;
        this.properties = properties;
        this.validationSchema = SchemaNames.require(validationSchema);
    }

    /**
     * Hibernate takes this one at bootstrap, for schema validation and for
     * reading dialect metadata, and it does not name a tenant. Pointing it at
     * one real tenant schema is what lets {@code ddl-auto: validate} work at
     * all here — and it validates that one schema, not the other fifty.
     */
    @Override
    public Connection getAnyConnection() throws SQLException {
        Connection connection = dataSource.getConnection();
        applySearchPath(connection, validationSchema);
        return connection;
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        resetAndClose(connection);
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        Connection connection = dataSource.getConnection();
        try {
            applySearchPath(connection, properties.schemaFor(tenantIdentifier));
        } catch (SQLException | RuntimeException e) {
            connection.close();
            throw e;
        }
        return connection;
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        resetAndClose(connection);
    }

    /**
     * False on purpose. Aggressive release hands the connection back after
     * every statement and takes a new one for the next, which would re-run the
     * {@code SET search_path} several times inside one transaction.
     */
    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return MultiTenantConnectionProvider.class.equals(unwrapType)
                || SchemaPerTenantConnectionProvider.class.equals(unwrapType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T unwrap(Class<T> unwrapType) {
        if (isUnwrappableAs(unwrapType)) {
            return (T) this;
        }
        throw new IllegalArgumentException("Cannot unwrap to " + unwrapType);
    }

    public long searchPathSwitches() {
        return searchPathSwitches.get();
    }

    public long searchPathResets() {
        return searchPathResets.get();
    }

    private void applySearchPath(Connection connection, String schema) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("set search_path to " + SchemaNames.require(schema));
        }
        searchPathSwitches.incrementAndGet();
    }

    private void resetAndClose(Connection connection) throws SQLException {
        try {
            if (properties.isResetSearchPathOnRelease()) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("set search_path to " + NEUTRAL_SEARCH_PATH);
                }
                searchPathResets.incrementAndGet();
            }
        } finally {
            connection.close();
        }
    }
}
