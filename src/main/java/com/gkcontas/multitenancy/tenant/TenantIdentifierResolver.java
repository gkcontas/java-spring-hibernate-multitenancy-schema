package com.gkcontas.multitenancy.tenant;

import org.hibernate.cfg.MultiTenancySettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

/**
 * The single point Hibernate asks "whose data is this?" — once per session for
 * the schema strategy, and once per insert to fill every {@code @TenantId}
 * column in the discriminator model. Registered under
 * {@link MultiTenancySettings#TENANT_IDENTIFIER_RESOLVER} on both persistence
 * units.
 *
 * <p>The fallback is the interesting part. With it on, work that lost the
 * tenant context writes into the fallback schema and returns 200; with it off,
 * the same work throws. Scenario {@code tenant-resolution} measures both.
 */
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

    private final TenantProperties properties;

    public TenantIdentifierResolver(TenantProperties properties) {
        this.properties = properties;
    }

    @Override
    public String resolveCurrentTenantIdentifier() {
        return TenantContext.current().orElseGet(this::fallback);
    }

    /**
     * Returning true makes Hibernate refuse to reuse a session opened for a
     * different tenant instead of quietly serving it. It costs nothing and it
     * is off by default.
     */
    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }

    private String fallback() {
        if (!properties.isFallbackEnabled()) {
            throw new TenantNotResolvedException();
        }
        return properties.getFallbackTenant();
    }
}
