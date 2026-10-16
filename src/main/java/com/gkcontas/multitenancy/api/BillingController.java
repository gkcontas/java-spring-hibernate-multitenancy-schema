package com.gkcontas.multitenancy.api;

import com.gkcontas.multitenancy.billing.BillingTotals;
import com.gkcontas.multitenancy.billing.InvoiceStatus;
import com.gkcontas.multitenancy.schema.model.Customer;
import com.gkcontas.multitenancy.schema.repository.CustomerRepository;
import com.gkcontas.multitenancy.schema.repository.InvoiceRepository;
import com.gkcontas.multitenancy.tenant.TenantCatalog;
import com.gkcontas.multitenancy.tenant.TenantContext;
import com.gkcontas.multitenancy.tenant.TenantNotResolvedException;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The billing API over the schema-per-tenant model. Nothing in here mentions a
 * tenant: the filter put it in the context, the resolver answered with it, and
 * the connection can only see one schema. That absence is the whole selling
 * point of the strategy.
 */
@RestController
@RequestMapping("/api/invoices")
public class BillingController {

    private final InvoiceRepository invoices;
    private final CustomerRepository customers;
    private final BillingTotals totals;
    private final TenantCatalog catalog;

    public BillingController(InvoiceRepository invoices,
                             CustomerRepository customers,
                             BillingTotals totals,
                             TenantCatalog catalog) {
        this.invoices = invoices;
        this.customers = customers;
        this.totals = totals;
        this.catalog = catalog;
    }

    @GetMapping
    @Transactional(transactionManager = "schemaTransactionManager", readOnly = true)
    public List<InvoiceView> byStatus(@RequestParam InvoiceStatus status) {
        catalog.requireKnown(currentTenant());
        return invoices.findByStatusOrderByIssuedOnDesc(status).stream()
                .map(invoice -> new InvoiceView(invoice.getId(), invoice.getNumber(),
                        invoice.getStatus().name(), invoice.getAmountCents(),
                        invoice.getIssuedOn()))
                .toList();
    }

    @GetMapping("/total")
    @Transactional(transactionManager = "schemaTransactionManager", readOnly = true)
    public TotalView total(@RequestParam InvoiceStatus status) {
        String tenantId = currentTenant();
        catalog.requireKnown(tenantId);
        return new TotalView(tenantId, status.name(), invoices.totalCentsByStatus(status));
    }

    /** The same total through the tenant-aware cache, so the two can be compared live. */
    @GetMapping("/total/cached")
    public TotalView cachedTotal(@RequestParam InvoiceStatus status) {
        String tenantId = currentTenant();
        catalog.requireKnown(tenantId);
        return new TotalView(tenantId, status.name(), totals.totalTenantAware(status));
    }

    @PostMapping("/customers")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional(transactionManager = "schemaTransactionManager")
    public CustomerView createCustomer(@Valid @RequestBody CreateCustomerRequest request) {
        catalog.requireKnown(currentTenant());
        Customer customer = new Customer();
        customer.setName(request.name());
        customer.setEmail(request.email());
        customer.setCreatedAt(OffsetDateTime.now());
        Customer saved = customers.save(customer);
        return new CustomerView(saved.getId(), saved.getName(), saved.getEmail());
    }

    private static String currentTenant() {
        return TenantContext.current().orElseThrow(TenantNotResolvedException::new);
    }
}
