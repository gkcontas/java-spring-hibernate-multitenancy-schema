package com.gkcontas.multitenancy.tenant;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

/**
 * Registers the named tenants once the context is up. Their schemas were
 * already created and migrated while the schema-per-tenant persistence unit was
 * being built - the unit cannot validate against a schema that does not exist
 * yet - so all that is left here is the registry row.
 */
@Component
public class TenantBootstrap implements ApplicationRunner {

    private final TenantProperties properties;
    private final TenantProvisioner provisioner;

    public TenantBootstrap(TenantProperties properties, TenantProvisioner provisioner) {
        this.properties = properties;
        this.provisioner = provisioner;
    }

    @Override
    public void run(ApplicationArguments args) {
        properties.getBootstrap().forEach(tenant ->
                provisioner.register(tenant.getId(), tenant.getDisplayName()));
    }
}
