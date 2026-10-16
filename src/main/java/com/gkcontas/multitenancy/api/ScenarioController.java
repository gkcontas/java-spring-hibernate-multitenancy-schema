package com.gkcontas.multitenancy.api;

import com.gkcontas.multitenancy.measure.ProbeOptions;
import com.gkcontas.multitenancy.measure.ScenarioReport;
import com.gkcontas.multitenancy.scenario.Scenario;
import com.gkcontas.multitenancy.scenario.ScenarioRegistry;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scenarios")
public class ScenarioController {

    private final ScenarioRegistry registry;

    public ScenarioController(ScenarioRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public List<ScenarioView> list() {
        return registry.all().stream()
                .map(scenario -> new ScenarioView(scenario.name(), scenario.question()))
                .toList();
    }

    @PostMapping
    public List<ScenarioReport> runAll(@RequestParam(required = false) Integer warmups,
                                       @RequestParam(required = false) Integer repeats) {
        return registry.runAll(ProbeOptions.of(warmups, repeats));
    }

    @PostMapping("/{name}")
    public ScenarioReport run(@PathVariable String name,
                              @RequestParam(required = false) Integer warmups,
                              @RequestParam(required = false) Integer repeats) {
        return registry.run(name, ProbeOptions.of(warmups, repeats));
    }

    @GetMapping("/{name}")
    public ScenarioView describe(@PathVariable String name) {
        Scenario scenario = registry.all().stream()
                .filter(candidate -> candidate.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new java.util.NoSuchElementException(
                        "Unknown scenario: " + name));
        return new ScenarioView(scenario.name(), scenario.question());
    }
}
