package com.gkcontas.multitenancy.tenant;

import java.util.regex.Pattern;

/**
 * Schema names reach SQL by concatenation, because neither
 * {@code SET search_path} nor a qualified table name takes a bind parameter.
 * Every place that builds such a string goes through here first.
 */
public final class SchemaNames {

    private static final Pattern SAFE = Pattern.compile("[a-z][a-z0-9_]{0,62}");

    private SchemaNames() {
    }

    public static String require(String schema) {
        if (schema == null || !SAFE.matcher(schema).matches()) {
            throw new IllegalArgumentException("Unsafe schema name: " + schema);
        }
        return schema;
    }
}
