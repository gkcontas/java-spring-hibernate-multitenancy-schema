package com.gkcontas.multitenancy;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * One PostgreSQL for the whole test run, started from a static initialiser.
 *
 * <p>Not {@code @Testcontainers}: Spring caches application contexts between
 * test classes, so a container tied to a class is stopped while a cached
 * context still points at it. Here it starts once and the JVM takes it down.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTestBase {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("billing")
                    .withUsername("billing")
                    .withPassword("billing");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // The tenant count the measurements use would make every test class pay
        // for 47 extra schemas. The functional tests work with the three named
        // tenants; the scenarios that need a tenant count seed it themselves.
        registry.add("app.tenants.bulk-count", () -> 3);
        registry.add("app.tenants.bulk-invoices-per-customer", () -> 6);
    }
}
