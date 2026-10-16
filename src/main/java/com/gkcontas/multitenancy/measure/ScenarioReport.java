package com.gkcontas.multitenancy.measure;

import java.time.OffsetDateTime;
import java.util.List;

public record ScenarioReport(String scenario,
                             String question,
                             String verdict,
                             OffsetDateTime measuredAt,
                             List<Variant> variants) {

    public static ScenarioReport of(String scenario, String question, String verdict,
                                    List<Variant> variants) {
        return new ScenarioReport(scenario, question, verdict, OffsetDateTime.now(), variants);
    }
}
