package com.gkcontas.multitenancy.shared.repository;

import com.gkcontas.multitenancy.billing.InvoiceStatus;
import com.gkcontas.multitenancy.shared.model.NaiveInvoice;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NaiveInvoiceRepository extends JpaRepository<NaiveInvoice, Long> {

    List<NaiveInvoice> findByStatusOrderByIssuedOnDesc(InvoiceStatus status);
}
