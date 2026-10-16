package com.gkcontas.multitenancy.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCustomerRequest(
        @NotBlank(message = "name is required")
        @Size(max = 120, message = "name must be at most 120 characters")
        String name,

        @NotBlank(message = "email is required")
        @Email(message = "email must be a well-formed address")
        @Size(max = 160, message = "email must be at most 160 characters")
        String email) {
}
