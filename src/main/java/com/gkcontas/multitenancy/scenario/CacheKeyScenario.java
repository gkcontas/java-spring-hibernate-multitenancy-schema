package com.gkcontas.multitenancy.scenario;

import com.gkcontas.multitenancy.billing.BillingTotals;
import com.gkcontas.multitenancy.billing.InvoiceStatus;
import com.gkcontas.multitenancy.config.CacheConfig;
import com.gkcontas.multitenancy.measure.ProbeOptions;
import com.gkcontas.multitenancy.measure.ScenarioReport;
import com.gkcontas.multitenancy.measure.Variant;
import com.gkcontas.multitenancy.measure.VariantKind;
import com.gkcontas.multitenancy.shared.model.SharedCustomer;
import com.gkcontas.multitenancy.shared.repository.SharedCustomerRepository;
import com.gkcontas.multitenancy.tenant.TenantContext;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A cache in front of a tenant-scoped read.
 *
 * <p>Everything that knows about tenants in this application — the resolver,
 * the {@code search_path}, the predicate {@code @TenantId} adds — lives below
 * the cache, and none of it is consulted on a hit. A key that does not name the
 * tenant is therefore not a performance problem; it is the fastest possible way
 * to answer one tenant with another tenant's number.
 *
 * <p>The third variant is the same question put to Hibernate's own second-level
 * cache, whose keys it builds itself.
 */
@Component
public class CacheKeyScenario implements Scenario {

    private static final String ONE = "acme";
    private static final String TWO = "globex";
    private static final InvoiceStatus STATUS = InvoiceStatus.PAID;

    private final BillingTotals totals;
    private final CacheManager caches;
    private final SharedCustomerRepository customers;
    private final TransactionTemplate transactions;
    private final EntityManagerFactory sharedUnit;
    private final Statistics statistics;

    public CacheKeyScenario(BillingTotals totals,
                            CacheManager caches,
                            SharedCustomerRepository customers,
                            @Qualifier("sharedTransactions") TransactionTemplate transactions,
                            @Qualifier("sharedEntityManagerFactory")
                            EntityManagerFactory sharedUnit) {
        this.totals = totals;
        this.caches = caches;
        this.customers = customers;
        this.transactions = transactions;
        this.sharedUnit = sharedUnit;
        this.statistics = sharedUnit.unwrap(SessionFactory.class).getStatistics();
    }

    @Override
    public String name() {
        return "cache-key";
    }

    @Override
    public String question() {
        return "acme asks for its paid total and the answer is cached. globex asks for "
                + "its own. What comes back?";
    }

    @Override
    public ScenarioReport run(ProbeOptions options) {
        long truthOne = TenantContext.callAs(ONE, () -> totals.readUncached(STATUS));
        long truthTwo = TenantContext.callAs(TWO, () -> totals.readUncached(STATUS));

        List<Variant> variants = List.of(
                throughCache("tenant-blind-key", VariantKind.PROBLEM,
                        CacheConfig.TENANT_BLIND, totals::totalTenantBlind,
                        truthOne, truthTwo),
                throughCache("tenant-in-key", VariantKind.FIX,
                        CacheConfig.TENANT_AWARE, totals::totalTenantAware,
                        truthOne, truthTwo),
                hibernateSecondLevelCache());

        return ScenarioReport.of(name(), question(),
                "acme's paid total is " + truthOne + " and globex's is " + truthTwo
                        + ". A cache the application adds has to be keyed by the tenant by "
                        + "hand, at every cache; Hibernate's own keys carry the tenant "
                        + "identifier, so the entity cache answers a foreign identifier with "
                        + "a miss instead of a row.",
                variants);
    }

    private Variant throughCache(String name, VariantKind kind, String cacheName,
                                 Function<InvoiceStatus, Long> read,
                                 long truthOne, long truthTwo) {
        Objects.requireNonNull(caches.getCache(cacheName)).clear();

        long first = TenantContext.callAs(ONE, () -> read.apply(STATUS));
        long second = TenantContext.callAs(TWO, () -> read.apply(STATUS));

        boolean correct = first == truthOne && second == truthTwo;
        return Variant.builder()
                .name(name)
                .kind(kind)
                .outcome("acme got " + first + " (expected " + truthOne + "), globex got "
                        + second + " (expected " + truthTwo + ") -> "
                        + (correct ? "both right" : "globex was served acme's total"))
                .rows(correct ? 0L : 1L)
                .build();
    }

    /**
     * The same shape against Hibernate's entity cache: acme loads one of its
     * own customers, which caches it, and then globex asks for that identifier.
     */
    private Variant hibernateSecondLevelCache() {
        sharedUnit.getCache().evictAll();
        statistics.clear();

        long id = TenantContext.callAs(ONE, () -> transactions.execute(status ->
                customers.findAll().getFirst().getId()));

        TenantContext.runAs(ONE, () -> transactions.executeWithoutResult(status ->
                customers.findById(id)));
        TenantContext.runAs(ONE, () -> transactions.executeWithoutResult(status ->
                customers.findById(id)));

        Optional<SharedCustomer> asOther = TenantContext.callAs(TWO,
                () -> transactions.execute(status -> customers.findById(id)));

        return Variant.builder()
                .name("hibernate-entity-cache")
                .kind(VariantKind.FIX)
                .outcome("acme id " + id + ": " + statistics.getSecondLevelCacheHitCount()
                        + " cache hit(s), " + statistics.getSecondLevelCacheMissCount()
                        + " miss(es); the same id loaded as globex -> "
                        + (asOther.isEmpty() ? "empty" : "FOUND"))
                .rows(asOther.isEmpty() ? 0L : 1L)
                .build();
    }
}
