package com.gkcontas.multitenancy.measure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One statement text and how many times it was issued. Collapsing by text is
 * what makes a report readable: a loop over fifty tenant schemas is fifty
 * statements and one line.
 */
public record SqlStatement(String sql, int times) {

    public static List<SqlStatement> collapse(List<String> statements) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String sql : statements) {
            counts.merge(sql, 1, Integer::sum);
        }
        List<SqlStatement> collapsed = new ArrayList<>(counts.size());
        counts.forEach((sql, times) -> collapsed.add(new SqlStatement(sql, times)));
        return List.copyOf(collapsed);
    }
}
