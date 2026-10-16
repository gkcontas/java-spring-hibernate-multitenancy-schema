package com.gkcontas.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;

import com.gkcontas.multitenancy.api.CreateCustomerRequest;
import com.gkcontas.multitenancy.api.OnboardTenantRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The API end of the same question: the tenant arrives in a header, the filter
 * puts it in the context, and nothing below that mentions it again.
 */
class ApiTest extends ScenarioTestBase {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void servesEachTenantItsOwnInvoices() {
        ResponseEntity<Object[]> acme = get("/api/invoices?status=PAID", "acme");
        ResponseEntity<Object[]> globex = get("/api/invoices?status=PAID", "globex");

        assertThat(acme.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(acme.getBody()).isNotNull().isNotEmpty();
        assertThat(globex.getBody()).isNotNull().isNotEmpty();
        assertThat(acme.getBody().length).isNotEqualTo(globex.getBody().length);
    }

    @Test
    void bothModelsAnswerTheSameTotalForTheSameTenant() {
        String schemaModel = get("/api/invoices/total?status=PAID", "acme", String.class)
                .getBody();
        String sharedModel = get("/api/shared/invoices/total?status=PAID", "acme", String.class)
                .getBody();
        assertThat(schemaModel).isEqualTo(sharedModel);
    }

    @Test
    void theCachedTotalAgreesWithTheUncachedOne() {
        String direct = get("/api/invoices/total?status=PAID", "globex", String.class).getBody();
        String cached = get("/api/invoices/total/cached?status=PAID", "globex", String.class)
                .getBody();
        assertThat(cached).isEqualTo(direct);
    }

    @Test
    void theUnfilteredNativeCountIsLargerThanAnyTenantsOwn() {
        Long acrossTenants = get("/api/shared/invoices/count-across-tenants?status=PAID",
                "acme", Long.class).getBody();
        ResponseEntity<Object[]> acme = get("/api/shared/invoices?status=PAID", "acme");

        assertThat(acme.getBody()).isNotNull();
        assertThat(acrossTenants).isNotNull().isGreaterThan(acme.getBody().length);
    }

    @Test
    void refusesARequestWithNoTenantHeader() {
        ResponseEntity<String> response = http.getForEntity(
                "/api/invoices/total?status=PAID", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("X-Tenant-Id");
    }

    @Test
    void refusesARequestForATenantThatWasNeverRegistered() {
        ResponseEntity<String> response = get("/api/invoices?status=PAID", "nobody", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("Unknown tenant");
    }

    @Test
    void listsTheRegisteredTenantsWithTheirSchemas() {
        ResponseEntity<String> response = http.getForEntity("/api/tenants", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("\"tenantId\":\"acme\"")
                .contains("\"schemaName\":\"tenant_acme\"");
    }

    @Test
    void createsACustomerInTheSchemaOfTheTenantInTheHeader() {
        ResponseEntity<String> created = post("/api/invoices/customers", "initech",
                new CreateCustomerRequest("Api Probe", "api-probe@example.com"));

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(jdbc.queryForObject(
                "select count(*) from tenant_initech.customer where email = ?",
                Long.class, "api-probe@example.com")).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from tenant_acme.customer where email = ?",
                Long.class, "api-probe@example.com")).isZero();

        jdbc.update("delete from tenant_initech.customer where email = ?",
                "api-probe@example.com");
    }

    @Test
    void onboardsATenantThroughTheApi() {
        ResponseEntity<String> created = post("/api/tenants", null,
                new OnboardTenantRequest("apinewcomer", "Api Newcomer"));

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).contains("tenant_apinewcomer");
        assertThat(get("/api/invoices?status=PAID", "apinewcomer", String.class).getBody())
                .isEqualTo("[]");

        jdbc.update("delete from shared.tenant_registry where tenant_id = ?", "apinewcomer");
        jdbc.execute("drop schema if exists tenant_apinewcomer cascade");
    }

    @Test
    void listsTheScenariosWithTheQuestionEachOneAnswers() {
        ResponseEntity<String> response = http.getForEntity("/api/scenarios", String.class);
        assertThat(response.getBody())
                .contains("tenant-id-coverage")
                .contains("search-path-leak")
                .contains("question");
    }

    @Test
    void storesTheReportOfEveryScenarioItRan() {
        http.postForEntity("/api/scenarios/cache-key?warmups=0&repeats=1", null, String.class);
        ResponseEntity<String> stored = http.getForEntity("/api/measurements", String.class);
        assertThat(stored.getBody()).contains("cache-key").contains("tenant-blind-key");
    }

    private ResponseEntity<Object[]> get(String path, String tenantId) {
        return get(path, tenantId, Object[].class);
    }

    private <T> ResponseEntity<T> get(String path, String tenantId, Class<T> type) {
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(tenantId)), type);
    }

    private ResponseEntity<String> post(String path, String tenantId, Object body) {
        HttpHeaders headers = headers(tenantId);
        headers.add("Content-Type", "application/json");
        return http.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers),
                String.class);
    }

    private static HttpHeaders headers(String tenantId) {
        HttpHeaders headers = new HttpHeaders();
        if (tenantId != null) {
            headers.add("X-Tenant-Id", tenantId);
        }
        return headers;
    }
}
