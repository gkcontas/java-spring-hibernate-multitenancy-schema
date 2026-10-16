package com.gkcontas.multitenancy.shared.repository;

import com.gkcontas.multitenancy.shared.model.SharedCustomer;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SharedCustomerRepository extends JpaRepository<SharedCustomer, Long> {

    /**
     * Native, and therefore outside everything {@code @TenantId} guarantees.
     * Hibernate rewrites the HQL it parses; a string handed to the driver is
     * handed to the driver.
     */
    @Query(value = "select count(*) from shared.customer", nativeQuery = true)
    long countAcrossAllTenantsNative();

    @Query("select count(c) from SharedCustomer c")
    long countForCurrentTenant();

    @Query(value = "select id from shared.customer where tenant_id = :tenantId order by id",
            nativeQuery = true)
    List<Long> idsOfNative(String tenantId);
}
