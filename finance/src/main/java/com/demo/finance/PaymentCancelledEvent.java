package com.demo.finance;

import com.demo.eventbridge.IntegrationEvent;

public record PaymentCancelledEvent(
        String requestId,
        String processId,
        String reason) implements IntegrationEvent {

    @Override
    public String type() {
        return "finance.payment-cancelled";
    }

    @Override
    public String correlationId() {
        return processId;
    }
}
