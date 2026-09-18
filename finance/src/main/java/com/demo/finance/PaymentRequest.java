package com.demo.finance;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentRequest(
        String id,
        String processId,
        String payee,
        BigDecimal amount,
        String currency,
        String reference,
        Status status,
        Instant openedAt,
        Instant updatedAt) {

    public enum Status { OPEN, PAID, CANCELLED }

    PaymentRequest withStatus(Status newStatus) {
        return new PaymentRequest(id, processId, payee, amount, currency, reference, newStatus, openedAt,
                Instant.now());
    }
}
