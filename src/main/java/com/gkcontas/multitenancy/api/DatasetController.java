package com.gkcontas.multitenancy.api;

import com.gkcontas.multitenancy.dataset.DatasetSeeder;
import com.gkcontas.multitenancy.dataset.SeedReport;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dataset")
public class DatasetController {

    private final DatasetSeeder seeder;

    public DatasetController(DatasetSeeder seeder) {
        this.seeder = seeder;
    }

    /** Every tenant, both models. This is the dataset the README's numbers come from. */
    @PostMapping
    public SeedReport seedAll() {
        return seeder.seedAll();
    }

    /** The three named tenants only, for a quick look at the functional scenarios. */
    @PostMapping("/named")
    public SeedReport seedNamed() {
        return seeder.seedNamed();
    }
}
