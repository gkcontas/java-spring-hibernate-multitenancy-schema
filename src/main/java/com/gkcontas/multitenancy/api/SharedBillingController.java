package com.gkcontas.multitenancy.api;

import com.gkcontas.multitenancy.billing.InvoiceStatus;
import com.gkcontas.multitenancy.shared.repository.SharedInvoiceRepository;
import com.gkcontas.multitenancy.tenant.TenantCatalog;
import com.gkcontas.multitenancy.tenant.TenantContext;
import com.gkcontas.multitenancy.tenant.TenantNotResolvedException;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The same API over the discriminator-column model. The code is just as free of
 * tenant mentions as the other one - {@code @TenantId} adds the predicate - and
 * that is exactly what makes the difference easy to miss: the isolation is a
 * rewrite Hibernate performs, not a boundary the database enforces.
 */
@RestController
@RequestMapping("/api/shared/invoices")
public class SharedBillingController {

    private final SharedInvoiceRepository invoices;
    private final TenantCatalog catalog;

    public SharedBillingController(SharedInvoiceRepository invoices, TenantCatalog catalog) {
        this.invoices = invoices;
        this.catalog = catalog;
    }

    @GetMapping
    @Transactional(transactionManager = "sharedTransactionManager", readOnly = true)
    public List<InvoiceView> byStatus(@RequestParam InvoiceStatus status) {
        catalog.requireKnown(currentTenant());
        return invoices.findByStatusOrderByIssuedOnDesc(status).stream()
                .map(invoice -> new InvoiceView(invoice.getId(), invoice.getNumber(),
                        invoice.getStatus().name(), invoice.getAmountCents(),
                        invoice.getIssuedOn()))
                .toList();
    }

    @GetMapping("/total")
    @Transactional(transactionManager = "sharedTransactionManager", readOnly = true)
    public TotalView total(@RequestParam InvoiceStatus status) {
        String tenantId = currentTenant();
        catalog.requireKnown(tenantId);
        return new TotalView(tenantId, status.name(),
                invoices.totalCentsNative(tenantId, status.name()));
    }

    /**
     * The count of the same invoices in native SQL, left unfiltered on purpose
     * and named for what it is. Scenario {@code tenant-id-coverage} measures it.
     */
    @GetMapping("/count-across-tenants")
    @Transactional(transactionManager = "sharedTransactionManager", readOnly = true)
    public long countAcrossTenants(@RequestParam InvoiceStatus status) {
        return invoices.countByStatusNative(status.name());
    }

    private static String currentTenant() {
        return TenantContext.current().orElseThrow(TenantNotResolvedException::new);
    }
}
