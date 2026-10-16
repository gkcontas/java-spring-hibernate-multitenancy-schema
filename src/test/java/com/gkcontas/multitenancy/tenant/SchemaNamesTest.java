package com.gkcontas.multitenancy.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The guard matters more here than it looks: a schema name cannot be a bind
 * parameter, so it is the one value in this application that reaches SQL as
 * text.
 */
class SchemaNamesTest {

    @Test
    void acceptsATenantSchemaName() {
        assertThat(SchemaNames.require("tenant_acme")).isEqualTo("tenant_acme");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "tenant_acme; drop schema shared cascade",
            "tenant_acme\"",
            "Tenant_Acme",
            "1tenant",
            "tenant acme",
            "tenant-acme",
            ""
    })
    void rejectsAnythingThatIsNotAPlainIdentifier(String candidate) {
        assertThatThrownBy(() -> SchemaNames.require(candidate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsafe schema name");
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> SchemaNames.require(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
