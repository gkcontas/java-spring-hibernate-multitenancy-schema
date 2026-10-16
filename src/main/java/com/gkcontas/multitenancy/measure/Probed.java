package com.gkcontas.multitenancy.measure;

import java.util.List;

/** What one probed unit of work produced, and what it cost. */
public record Probed<T>(T value,
                        long statements,
                        long entities,
                        double medianMs,
                        List<SqlStatement> sql) {
}
