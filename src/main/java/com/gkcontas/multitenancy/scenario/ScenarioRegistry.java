package com.gkcontas.multitenancy.scenario;

import com.gkcontas.multitenancy.measure.MeasurementStore;
import com.gkcontas.multitenancy.measure.ProbeOptions;
import com.gkcontas.multitenancy.measure.ScenarioReport;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Component;

@Component
public class ScenarioRegistry {

    private final Map<String, Scenario> scenarios = new LinkedHashMap<>();
    private final MeasurementStore store;

    public ScenarioRegistry(List<Scenario> discovered, MeasurementStore store) {
        discovered.stream()
                .sorted((a, b) -> a.name().compareTo(b.name()))
                .forEach(scenario -> scenarios.put(scenario.name(), scenario));
        this.store = store;
    }

    public List<Scenario> all() {
        return List.copyOf(scenarios.values());
    }

    public ScenarioReport run(String name, ProbeOptions options) {
        Scenario scenario = scenarios.get(name);
        if (scenario == null) {
            throw new NoSuchElementException("Unknown scenario: " + name);
        }
        ScenarioReport report = scenario.run(options);
        store.record(report);
        return report;
    }

    public List<ScenarioReport> runAll(ProbeOptions options) {
        return scenarios.keySet().stream().map(name -> run(name, options)).toList();
    }
}
