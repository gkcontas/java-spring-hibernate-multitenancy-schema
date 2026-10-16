package com.gkcontas.multitenancy.api;

public record TotalView(String tenantId, String status, long totalCents) {
}
