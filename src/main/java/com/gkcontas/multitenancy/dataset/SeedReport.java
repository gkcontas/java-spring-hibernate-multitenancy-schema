package com.gkcontas.multitenancy.dataset;

public record SeedReport(int tenants,
                         int customersPerModel,
                         int invoicesPerModel,
                         long tenantSchemaRelations,
                         long sharedSchemaRelations,
                         long elapsedMs) {
}
