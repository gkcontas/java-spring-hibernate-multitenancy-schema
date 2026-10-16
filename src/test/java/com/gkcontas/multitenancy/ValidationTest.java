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

/**
 * The messages are declared on every constraint on purpose: the JVM here runs
 * under a pt-BR locale, and Bean Validation's defaults are resolved from it, so
 * an English contract would answer in Portuguese.
 */
class ValidationTest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate http;

    @Test
    void refusesACustomerWithNoName() {
        ResponseEntity<String> response = post("/api/invoices/customers", "acme",
                new CreateCustomerRequest("  ", "someone@example.com"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("name is required");
    }

    @Test
    void refusesAnAddressThatIsNotAnEmail() {
        ResponseEntity<String> response = post("/api/invoices/customers", "acme",
                new CreateCustomerRequest("Someone", "not-an-email"));

        assertThat(response.getBody()).contains("email must be a well-formed address");
    }

    @Test
    void refusesANameLongerThanTheColumn() {
        ResponseEntity<String> response = post("/api/invoices/customers", "acme",
                new CreateCustomerRequest("x".repeat(121), "someone@example.com"));

        assertThat(response.getBody()).contains("name must be at most 120 characters");
    }

    @Test
    void refusesATenantIdentifierThatWouldNotBeASafeSchemaName() {
        ResponseEntity<String> response = post("/api/tenants", null,
                new OnboardTenantRequest("Acme; drop schema shared cascade", "Acme"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody())
                .contains("tenantId must be lowercase letters and digits");
    }

    @Test
    void refusesATenantWithNoDisplayName() {
        ResponseEntity<String> response = post("/api/tenants", null,
                new OnboardTenantRequest("validid", ""));

        assertThat(response.getBody()).contains("displayName is required");
    }

    @Test
    void reportsEveryInvalidFieldAtOnce() {
        ResponseEntity<String> response = post("/api/invoices/customers", "acme",
                new CreateCustomerRequest("", "nope"));

        assertThat(response.getBody())
                .contains("Validation failed")
                .contains("name")
                .contains("email");
    }

    @Test
    void refusesAStatusThatIsNotAnInvoiceStatus() {
        ResponseEntity<String> response = http.exchange(
                "/api/invoices?status=SOMETHING", HttpMethod.GET,
                new HttpEntity<>(headers("acme")), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
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
