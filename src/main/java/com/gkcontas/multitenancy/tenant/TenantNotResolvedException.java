package com.gkcontas.multitenancy.tenant;

/**
 * Thrown when work reaches the persistence layer with no tenant in context and
 * the fallback is off. Noisy on purpose: the alternative is writing a row into
 * whichever schema the fallback names.
 */
public class TenantNotResolvedException extends RuntimeException {

    public TenantNotResolvedException() {
        super("No tenant in context: the request carried no X-Tenant-Id, or the "
                + "context did not reach this thread");
    }
}
