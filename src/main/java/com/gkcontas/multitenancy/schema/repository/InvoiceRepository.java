package com.gkcontas.multitenancy.schema.repository;

import com.gkcontas.multitenancy.billing.InvoiceStatus;
import com.gkcontas.multitenancy.schema.model.Invoice;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    /**
     * No tenant predicate anywhere in this interface. There is nothing to
     * filter: the connection running it can only see one schema.
     */
    List<Invoice> findByStatusOrderByIssuedOnDesc(InvoiceStatus status);

    @Query("select coalesce(sum(i.amountCents), 0) from SchemaInvoice i where i.status = :status")
    long totalCentsByStatus(InvoiceStatus status);

    @Query("select count(i) from SchemaInvoice i")
    long countAll();
}
