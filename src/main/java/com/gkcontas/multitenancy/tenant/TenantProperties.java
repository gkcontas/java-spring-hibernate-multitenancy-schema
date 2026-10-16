package com.gkcontas.multitenancy.tenant;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.tenants")
public class TenantProperties {

    private List<BootstrapTenant> bootstrap = new ArrayList<>();
    private int bulkCount = 47;
    private int bulkCustomers = 10;
    private int bulkInvoicesPerCustomer = 100;
    private String schemaPrefix = "tenant_";
    private boolean resetSearchPathOnRelease = true;
    private String fallbackTenant = "acme";
    private boolean fallbackEnabled = true;

    @Getter
    @Setter
    public static class BootstrapTenant {
        private String id;
        private String displayName;
        private int customers = 10;
        private int invoicesPerCustomer = 10;
    }

    public String schemaFor(String tenantId) {
        return schemaPrefix + tenantId;
    }

    public List<String> bootstrapIds() {
        return bootstrap.stream().map(BootstrapTenant::getId).toList();
    }
}
