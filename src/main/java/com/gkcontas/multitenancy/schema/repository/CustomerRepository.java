package com.gkcontas.multitenancy.schema.repository;

import com.gkcontas.multitenancy.schema.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
}
