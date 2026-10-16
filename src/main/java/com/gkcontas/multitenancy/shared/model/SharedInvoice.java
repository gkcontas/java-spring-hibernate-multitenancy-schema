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
 * The tenant-aware table: unique on {@code (tenant_id, number)}, indexed on
 * {@code (tenant_id, status, issued_on)}, and with a composite foreign key that
 * cannot point at another tenant's customer.
 *
 * <p>The customer is mapped as a raw identifier rather than as a
 * {@code @ManyToOne}, so a scenario can offer the database an identifier that
 * belongs to another tenant and see which constraint catches it. Through an
 * association Hibernate would refuse earlier, on its own terms, and the
 * database's answer would never be heard.
 */
@Entity(name = "SharedInvoice")
@Table(name = "invoice")
@Getter
@Setter
@NoArgsConstructor
public class SharedInvoice {

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
