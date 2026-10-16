package com.gkcontas.multitenancy.api;

import java.time.LocalDate;

public record InvoiceView(Long id,
                          String number,
                          String status,
                          long amountCents,
                          LocalDate issuedOn) {
}
