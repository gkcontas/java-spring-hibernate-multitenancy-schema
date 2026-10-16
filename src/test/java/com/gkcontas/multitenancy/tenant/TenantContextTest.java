package com.gkcontas.multitenancy.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TenantContextTest {

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void isEmptyUntilSomethingSetsIt() {
        assertThat(TenantContext.current()).isEmpty();
    }

    @Test
    void restoresThePreviousTenantAfterNestedWork() {
        TenantContext.set("acme");
        TenantContext.runAs("globex",
                () -> assertThat(TenantContext.current()).contains("globex"));
        assertThat(TenantContext.current()).contains("acme");
    }

    @Test
    void leavesNothingBehindWhenThereWasNothingBefore() {
        TenantContext.runAs("acme", () -> {
        });
        assertThat(TenantContext.current()).isEmpty();
    }

    @Test
    void restoresThePreviousTenantEvenWhenTheWorkThrows() {
        TenantContext.set("acme");
        try {
            TenantContext.runAs("globex", () -> {
                throw new IllegalStateException("boom");
            });
        } catch (IllegalStateException expected) {
            // The point is what is left in the context afterwards.
        }
        assertThat(TenantContext.current()).contains("acme");
    }

    @Test
    void doesNotReachAnotherThreadOnItsOwn() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>("unset");
        TenantContext.set("globex");
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            executor.submit(() -> seen.set(TenantContext.current().orElse(null))).get();
        }
        assertThat(seen.get()).isNull();
    }

    @Test
    void reachesAnotherThreadWhenTheTaskIsWrapped() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>("unset");
        TenantContext.set("globex");
        Runnable task = TenantContext.propagating(
                () -> seen.set(TenantContext.current().orElse(null)));
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            executor.submit(task).get();
        }
        assertThat(seen.get()).isEqualTo("globex");
    }

    @Test
    void leavesTheWorkerThreadCleanAfterAWrappedTask() throws Exception {
        AtomicReference<String> after = new AtomicReference<>("unset");
        TenantContext.set("globex");
        Runnable task = TenantContext.propagating(() -> {
        });
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            executor.submit(task).get();
            executor.submit(() -> after.set(TenantContext.current().orElse(null))).get();
        }
        assertThat(after.get()).isNull();
    }
}
