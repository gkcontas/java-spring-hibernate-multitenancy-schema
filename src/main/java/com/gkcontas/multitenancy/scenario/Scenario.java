package com.gkcontas.multitenancy.scenario;

import com.gkcontas.multitenancy.measure.ProbeOptions;
import com.gkcontas.multitenancy.measure.ScenarioReport;

public interface Scenario {

    /** Stable kebab-case identifier, used in the API and in the README. */
    String name();

    /** The question every variant of this scenario answers. */
    String question();

    ScenarioReport run(ProbeOptions options);
}
