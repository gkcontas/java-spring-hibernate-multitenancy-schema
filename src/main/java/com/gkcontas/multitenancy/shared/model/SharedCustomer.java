package com.gkcontas.multitenancy.shared.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.TenantId;

/**
 * Discriminator-column model. {@link TenantId} is Hibernate's native support:
 * the column is filled from the current tenant resolver on insert, every query
 * Hibernate writes gains a predicate on it, and the field is treated as
 * immutable afterwards.
 *
 * <p>What it does not do is the subject of scenario {@code tenant-id-coverage}.
 */
@Entity(name = "SharedCustomer")
@Table(name = "customer")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@Getter
@Setter
@NoArgsConstructor
public class SharedCustomer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 40)
    private String tenantId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 160)
    private String email;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
