package com.demo.finance;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentRequest(
        String id,
        String invoice,
        String payee,
        BigDecimal amount,
        String currency,
        Status status,
        Instant openedAt,
        Instant updatedAt) {

    public enum Status { PENDING, PROCESSING, PAID }

    PaymentRequest withStatus(Status newStatus) {
        return new PaymentRequest(id, invoice, payee, amount, currency, newStatus, openedAt, Instant.now());
    }
}
