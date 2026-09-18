package com.demo.logistics;

import java.math.BigDecimal;
import java.time.Instant;

import com.demo.eventbridge.IntegrationEvent;

public record CargoArrivedEvent(
        String container,
        String vessel,
        String port,
        String invoice,
        BigDecimal terminalCharges,
        Instant arrivedAt) implements IntegrationEvent {

    @Override
    public String type() {
        return "logistics.cargo-arrived";
    }
}
