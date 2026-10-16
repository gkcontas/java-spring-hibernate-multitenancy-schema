package com.gkcontas.multitenancy.measure;

import java.util.List;
import lombok.Builder;

/**
 * One way of doing the thing the scenario is about, and what it cost.
 *
 * <p>{@code outcome} carries what the variant actually answered, because in a
 * multi-tenancy project the wrong answer is usually not slower - it is a number
 * from somebody else's data, returned promptly.
 */
@Builder
public record Variant(String name,
                      VariantKind kind,
                      String outcome,
                      Long statements,
                      Long entities,
                      Long rows,
                      Double medianMs,
                      List<SqlStatement> sql) {

    public static VariantBuilder from(String name, VariantKind kind, Probed<?> probed) {
        return Variant.builder()
                .name(name)
                .kind(kind)
                .statements(probed.statements())
                .entities(probed.entities())
                .medianMs(probed.medianMs())
                .sql(probed.sql());
    }
}
