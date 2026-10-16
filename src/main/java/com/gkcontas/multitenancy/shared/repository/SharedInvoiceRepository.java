package com.gkcontas.multitenancy.shared.repository;

import com.gkcontas.multitenancy.billing.InvoiceStatus;
import com.gkcontas.multitenancy.shared.model.SharedInvoice;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SharedInvoiceRepository extends JpaRepository<SharedInvoice, Long> {

    /** Hibernate adds the tenant predicate to this one. */
    List<SharedInvoice> findByStatusOrderByIssuedOnDesc(InvoiceStatus status);

    @Query("select count(i) from SharedInvoice i where i.status = :status")
    long countByStatusHql(InvoiceStatus status);

    /** The same count, written by hand, and therefore across every tenant. */
    @Query(value = "select count(*) from shared.invoice where status = :status",
            nativeQuery = true)
    long countByStatusNative(String status);

    /**
     * A bulk mutation in HQL. Whether Hibernate narrows it to the current
     * tenant is measured rather than assumed - the generated SQL is in the
     * report.
     */
    @Modifying
    @Query("update SharedInvoice i set i.status = :status where i.status = :from")
    int bulkRestatus(InvoiceStatus from, InvoiceStatus status);

    @Query(value = """
            select tenant_id, count(*), coalesce(sum(amount_cents), 0)
              from shared.invoice
             group by tenant_id
             order by tenant_id
            """, nativeQuery = true)
    List<Object[]> totalsPerTenant();

    @Query(value = """
            select coalesce(sum(amount_cents), 0)
              from shared.invoice
             where tenant_id = :tenantId and status = :status
            """, nativeQuery = true)
    long totalCentsNative(String tenantId, String status);
}
