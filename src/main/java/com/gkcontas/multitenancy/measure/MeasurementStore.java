package com.gkcontas.multitenancy.measure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Keeps the last report of each scenario, so the whole run can be read at once. */
@Component
public class MeasurementStore {

    private final Map<String, ScenarioReport> reports = new ConcurrentHashMap<>();

    public void record(ScenarioReport report) {
        reports.put(report.scenario(), report);
    }

    public List<ScenarioReport> all() {
        List<ScenarioReport> sorted = new ArrayList<>(reports.values());
        sorted.sort((a, b) -> a.scenario().compareTo(b.scenario()));
        return List.copyOf(sorted);
    }

    public ScenarioReport get(String scenario) {
        return reports.get(scenario);
    }

    public void clear() {
        reports.clear();
    }
}
