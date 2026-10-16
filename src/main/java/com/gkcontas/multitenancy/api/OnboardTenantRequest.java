package com.gkcontas.multitenancy.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The identifier becomes a schema name, so the pattern is not cosmetic: it is
 * the only thing between a request body and an identifier concatenated into
 * {@code SET search_path}.
 */
public record OnboardTenantRequest(
        @NotBlank(message = "tenantId is required")
        @Pattern(regexp = "[a-z][a-z0-9]{1,30}",
                message = "tenantId must be lowercase letters and digits, starting with a letter")
        String tenantId,

        @NotBlank(message = "displayName is required")
        @Size(max = 120, message = "displayName must be at most 120 characters")
        String displayName) {
}
