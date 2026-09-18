package com.demo.finance;

import java.math.BigDecimal;

import com.demo.eventbridge.IntegrationEvent;

public record PaymentCompletedEvent(
        String requestId,
        String processId,
        String payee,
        BigDecimal amount,
        String currency) implements IntegrationEvent {

    @Override
    public String type() {
        return "finance.payment-completed";
    }

    @Override
    public String correlationId() {
        return processId;
    }
}
