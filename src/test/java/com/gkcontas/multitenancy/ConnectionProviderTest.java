package com.gkcontas.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gkcontas.multitenancy.tenant.SchemaPerTenantConnectionProvider;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ConnectionProviderTest extends IntegrationTestBase {

    @Autowired
    private SchemaPerTenantConnectionProvider provider;

    @Test
    void pointsTheConnectionAtTheTenantSchema() throws SQLException {
        Connection connection = provider.getConnection("globex");
        try {
            assertThat(searchPath(connection)).isEqualTo("tenant_globex");
        } finally {
            provider.releaseConnection("globex", connection);
        }
    }

    @Test
    void resolvesAnUnqualifiedTableNameInsideTheTenantSchema() throws SQLException {
        Connection connection = provider.getConnection("initech");
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("select count(*) from customer")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getLong(1)).isNotNegative();
        } finally {
            provider.releaseConnection("initech", connection);
        }
    }

    @Test
    void cleansTheConnectionOnTheWayBack() throws SQLException {
        long resetsBefore = provider.searchPathResets();
        Connection connection = provider.getConnection("acme");
        provider.releaseConnection("acme", connection);
        assertThat(provider.searchPathResets()).isGreaterThan(resetsBefore);
    }

    @Test
    void countsOneSwitchPerConnectionHandedOut() throws SQLException {
        long before = provider.searchPathSwitches();
        Connection connection = provider.getConnection("acme");
        provider.releaseConnection("acme", connection);
        assertThat(provider.searchPathSwitches()).isEqualTo(before + 1);
    }

    @Test
    void refusesATenantWhoseSchemaNameWouldNotBeAnIdentifier() {
        assertThatThrownBy(() -> provider.getConnection("acme\"; drop schema shared cascade"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsafe schema name");
    }

    @Test
    void doesNotReleaseTheConnectionAfterEveryStatement() {
        assertThat(provider.supportsAggressiveRelease()).isFalse();
    }

    @Test
    void unwrapsToItself() {
        assertThat(provider.unwrap(SchemaPerTenantConnectionProvider.class)).isSameAs(provider);
        assertThatThrownBy(() -> provider.unwrap(String.class))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static String searchPath(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("show search_path")) {
            rows.next();
            return rows.getString(1);
        }
    }
}
