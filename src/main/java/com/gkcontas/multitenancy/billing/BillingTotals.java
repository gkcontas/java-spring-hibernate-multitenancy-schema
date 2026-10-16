package com.gkcontas.multitenancy.billing;

import com.gkcontas.multitenancy.config.CacheConfig;
import com.gkcontas.multitenancy.shared.repository.SharedInvoiceRepository;
import com.gkcontas.multitenancy.tenant.TenantContext;
import com.gkcontas.multitenancy.tenant.TenantNotResolvedException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * The same read, cached two ways. Both methods go to the database for the
 * current tenant only; the difference is entirely in the key.
 */
@Service
public class BillingTotals {

    private final SharedInvoiceRepository invoices;

    public BillingTotals(SharedInvoiceRepository invoices) {
        this.invoices = invoices;
    }

    /** Keyed by the status alone - which is to say, keyed by half the question. */
    @Cacheable(value = CacheConfig.TENANT_BLIND, key = "#status")
    public long totalTenantBlind(InvoiceStatus status) {
        return read(status);
    }

    @Cacheable(value = CacheConfig.TENANT_AWARE, keyGenerator = "tenantAwareKeyGenerator")
    public long totalTenantAware(InvoiceStatus status) {
        return read(status);
    }

    public long readUncached(InvoiceStatus status) {
        return read(status);
    }

    private long read(InvoiceStatus status) {
        String tenantId = TenantContext.current().orElseThrow(TenantNotResolvedException::new);
        return invoices.totalCentsNative(tenantId, status.name());
    }
}
