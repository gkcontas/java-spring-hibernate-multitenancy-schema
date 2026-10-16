package com.gkcontas.multitenancy.billing;

/** Shared by both persistence models, so a scenario can compare like with like. */
public enum InvoiceStatus {
    DRAFT,
    ISSUED,
    PAID,
    OVERDUE,
    CANCELLED
}
