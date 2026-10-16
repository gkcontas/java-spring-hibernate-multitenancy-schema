package com.gkcontas.multitenancy.tenant;

public class UnknownTenantException extends RuntimeException {

    public UnknownTenantException(String tenantId) {
        super("Unknown tenant: " + tenantId);
    }
}
