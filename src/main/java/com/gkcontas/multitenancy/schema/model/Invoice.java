package com.gkcontas.multitenancy.schema.model;

import com.gkcontas.multitenancy.billing.InvoiceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The unique key on {@code number} is global inside the schema, and inside the
 * schema global means per tenant. The discriminator model has to spell the same
 * guarantee out as {@code unique (tenant_id, number)}, and scenario
 * {@code tenant-aware-constraints} measures what happens when it does not.
 */
@Entity(name = "SchemaInvoice")
@Table(name = "invoice")
@Getter
@Setter
@NoArgsConstructor
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

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
