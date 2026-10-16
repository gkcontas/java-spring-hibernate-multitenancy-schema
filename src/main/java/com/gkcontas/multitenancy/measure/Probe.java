package com.gkcontas.multitenancy.measure;

import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Runs a unit of work under measurement: statement count and SQL text from the
 * {@link StatementRecorder}, entity count from Hibernate's own statistics, and
 * the median wall time of the timed runs.
 *
 * <p>Statistics are read from both persistence units and added together. A
 * scenario that compares schema-per-tenant against the discriminator column
 * runs each variant on a different {@code SessionFactory}, and a number that
 * came from only one of them would be a number about the wiring rather than
 * about the strategy.
 */
@Component
public class Probe {

    private final List<Statistics> statistics;

    public Probe(@Qualifier("schemaEntityManagerFactory") EntityManagerFactory schemaUnit,
                 @Qualifier("sharedEntityManagerFactory") EntityManagerFactory sharedUnit) {
        this.statistics = List.of(
                schemaUnit.unwrap(SessionFactory.class).getStatistics(),
                sharedUnit.unwrap(SessionFactory.class).getStatistics());
    }

    public <T> Probed<T> measure(ProbeOptions options, Supplier<T> work) {
        for (int i = 0; i < options.warmups(); i++) {
            work.get();
        }

        List<Double> times = new ArrayList<>(options.repeats());
        T value = null;
        long statements = 0;
        long entities = 0;
        List<SqlStatement> sql = List.of();

        for (int i = 0; i < options.repeats(); i++) {
            statistics.forEach(Statistics::clear);
            StatementRecorder.start();
            long startedAt = System.nanoTime();
            try {
                value = work.get();
            } finally {
                double elapsedMs = (System.nanoTime() - startedAt) / 1_000_000.0;
                List<String> captured = StatementRecorder.stop();
                times.add(elapsedMs);
                statements = captured.size();
                entities = statistics.stream().mapToLong(Statistics::getEntityLoadCount).sum();
                sql = SqlStatement.collapse(captured);
            }
        }

        return new Probed<>(value, statements, entities, median(times), sql);
    }

    public <T> Probed<T> measureOnce(Supplier<T> work) {
        return measure(ProbeOptions.ONCE, work);
    }

    private static double median(List<Double> times) {
        List<Double> sorted = new ArrayList<>(times);
        sorted.sort(Double::compareTo);
        int middle = sorted.size() / 2;
        double value = sorted.size() % 2 == 1
                ? sorted.get(middle)
                : (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
        return Math.round(value * 100.0) / 100.0;
    }
}
