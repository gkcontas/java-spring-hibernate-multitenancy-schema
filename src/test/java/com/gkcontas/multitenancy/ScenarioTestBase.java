package com.gkcontas.multitenancy;

import com.gkcontas.multitenancy.dataset.DatasetSeeder;
import com.gkcontas.multitenancy.measure.ProbeOptions;
import com.gkcontas.multitenancy.measure.ScenarioReport;
import com.gkcontas.multitenancy.measure.Variant;
import com.gkcontas.multitenancy.scenario.ScenarioRegistry;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Seeds the dataset once for the whole run. The scenarios read a fixture rather
 * than build one, and the ones that write roll back, so the order they run in
 * does not change their answers.
 */
public abstract class ScenarioTestBase extends IntegrationTestBase {

    private static final AtomicBoolean SEEDED = new AtomicBoolean();

    /** One repeat: the tests assert on outcomes and counts, not on times. */
    protected static final ProbeOptions FAST = new ProbeOptions(0, 1);

    @Autowired
    private DatasetSeeder seeder;

    @Autowired
    protected ScenarioRegistry registry;

    @BeforeEach
    void seedOnce() {
        if (SEEDED.compareAndSet(false, true)) {
            seeder.seedAll();
        }
    }

    protected ScenarioReport run(String scenario) {
        return registry.run(scenario, FAST);
    }

    protected Variant variant(ScenarioReport report, String name) {
        return report.variants().stream()
                .filter(variant -> variant.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "no variant " + name + " in " + report.scenario()));
    }
}
