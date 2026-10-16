package com.gkcontas.multitenancy.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.gkcontas.multitenancy.tenant.TenantContext;
import com.gkcontas.multitenancy.tenant.TenantNotResolvedException;
import java.time.Duration;
import java.util.Arrays;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Two caches with the same contents and different keys.
 *
 * <p>A cache in front of a tenant-scoped read is the shortest path to serving
 * one tenant's numbers to another, because the cache sits above everything that
 * knows about tenants: the resolver, the {@code search_path}, the predicate
 * {@code @TenantId} adds. None of them is consulted on a hit.
 */
@Configuration
public class CacheConfig {

    public static final String TENANT_BLIND = "tenantBlindTotals";
    public static final String TENANT_AWARE = "tenantAwareTotals";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(TENANT_BLIND, TENANT_AWARE);
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofMinutes(10)));
        return manager;
    }

    /**
     * The fix, and it has to be applied at every cache: the tenant is part of
     * the identity of the value, so it belongs in the key.
     */
    @Bean
    public KeyGenerator tenantAwareKeyGenerator() {
        return (target, method, params) -> TenantContext.current()
                .orElseThrow(TenantNotResolvedException::new)
                + ":" + method.getName() + ":" + Arrays.deepToString(params);
    }
}
