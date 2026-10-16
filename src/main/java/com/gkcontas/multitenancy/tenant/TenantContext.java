package com.gkcontas.multitenancy.tenant;

import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Holds the tenant of the work in progress. A {@link ThreadLocal} is the usual
 * choice and it is the right one for a request handled start to finish on the
 * request thread. It is also the whole weakness of the design: nothing copies
 * it into a thread the application did not start itself, and the failure is
 * silent on the way out.
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static Optional<String> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static void set(String tenantId) {
        CURRENT.set(tenantId);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static <T> T callAs(String tenantId, Callable<T> work) {
        String previous = CURRENT.get();
        CURRENT.set(tenantId);
        try {
            return work.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("tenant-scoped work failed for " + tenantId, e);
        } finally {
            restore(previous);
        }
    }

    public static void runAs(String tenantId, Runnable work) {
        callAs(tenantId, () -> {
            work.run();
            return null;
        });
    }

    /**
     * Wraps a task so it carries the tenant of the thread that created it. This
     * is the fix for the propagation hole, and it has to be applied at every
     * hand-off; there is no central place that does it for free.
     */
    public static Runnable propagating(Runnable task) {
        String captured = CURRENT.get();
        return () -> {
            String previous = CURRENT.get();
            CURRENT.set(captured);
            try {
                task.run();
            } finally {
                restore(previous);
            }
        };
    }

    private static void restore(String previous) {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }
}
