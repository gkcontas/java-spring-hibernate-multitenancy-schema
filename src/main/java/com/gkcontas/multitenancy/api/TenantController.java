package com.gkcontas.multitenancy.api;

import com.gkcontas.multitenancy.tenant.TenantCatalog;
import com.gkcontas.multitenancy.tenant.TenantProvisioner;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private final TenantCatalog catalog;
    private final TenantProvisioner provisioner;

    public TenantController(TenantCatalog catalog, TenantProvisioner provisioner) {
        this.catalog = catalog;
        this.provisioner = provisioner;
    }

    @GetMapping
    public List<TenantView> list() {
        return catalog.tenantIds().stream()
                .map(tenantId -> new TenantView(tenantId, catalog.schemaFor(tenantId)))
                .toList();
    }

    /**
     * Onboarding at runtime: create the schema, run the whole migration history
     * against it, and register it. The other tenants keep serving throughout,
     * which is the part that makes this strategy workable at all.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TenantView onboard(@Valid @RequestBody OnboardTenantRequest request) {
        provisioner.provision(request.tenantId());
        provisioner.register(request.tenantId(), request.displayName());
        return new TenantView(request.tenantId(), catalog.schemaFor(request.tenantId()));
    }
}
