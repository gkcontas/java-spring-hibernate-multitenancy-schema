package com.gkcontas.multitenancy.shared.model;

import com.gkcontas.multitenancy.billing.InvoiceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

/**
 * The same invoice, in the table that comes out when the discriminator is
 * treated as one more column: {@code unique (number)}, a foreign key that only
 * names {@code customer (id)}, and an index on {@code (status, issued_on)}.
 *
 * <p>The mapping is identical to {@link SharedInvoice} — {@code @TenantId} and
 * all. Everything that separates the two lives in the DDL, which is exactly the
 * point: Hibernate's filter is not a substitute for the constraints.
 */
@Entity(name = "NaiveInvoice")
@Table(name = "invoice_naive")
@Getter
@Setter
@NoArgsConstructor
public class NaiveInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 40)
    private String tenantId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(nullable = false, length = 40)
    private String number;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceStatus status;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(name = "issued_on", nullable = false)
    private LocalDate issuedOn;
}
