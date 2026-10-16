package com.gkcontas.multitenancy.measure;

import java.util.ArrayList;
import java.util.List;
import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Every statement Hibernate is about to send passes through
 * {@link #inspect(String)}.
 *
 * <p>Hibernate's own {@code Statistics} answers how many statements a unit of
 * work provoked; it says nothing about what they were. In a multi-tenant
 * project the text is the evidence: a claim that {@code @TenantId} filtered a
 * query is only worth something next to the {@code where tenant_id=?} it
 * added, and a claim that a native query did not filter is only worth something
 * next to the SQL that went out without it.
 *
 * <p>Hibernate instantiates this class itself from the class name in the
 * persistence unit properties, so it cannot be a Spring bean and cannot hold
 * collaborators. The buffer is a thread local, which also means a statement
 * issued from a thread the scenario did not start is not recorded — and that is
 * the honest answer, because that statement did not belong to the unit of work
 * either.
 */
public final class StatementRecorder implements StatementInspector {

    private static final ThreadLocal<List<String>> BUFFER = new ThreadLocal<>();

    public static void start() {
        BUFFER.set(new ArrayList<>());
    }

    public static List<String> stop() {
        List<String> captured = BUFFER.get();
        BUFFER.remove();
        return captured == null ? List.of() : List.copyOf(captured);
    }

    @Override
    public String inspect(String sql) {
        List<String> buffer = BUFFER.get();
        if (buffer != null) {
            buffer.add(sql);
        }
        return sql;
    }
}
