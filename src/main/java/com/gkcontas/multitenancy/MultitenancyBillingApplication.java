package com.gkcontas.multitenancy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class MultitenancyBillingApplication {

    public static void main(String[] args) {
        SpringApplication.run(MultitenancyBillingApplication.class, args);
    }
}
