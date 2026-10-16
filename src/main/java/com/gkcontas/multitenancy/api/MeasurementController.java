package com.gkcontas.multitenancy.api;

import com.gkcontas.multitenancy.measure.MeasurementStore;
import com.gkcontas.multitenancy.measure.ScenarioReport;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/measurements")
public class MeasurementController {

    private final MeasurementStore store;

    public MeasurementController(MeasurementStore store) {
        this.store = store;
    }

    @GetMapping
    public List<ScenarioReport> all() {
        return store.all();
    }

    @DeleteMapping
    public void clear() {
        store.clear();
    }
}
