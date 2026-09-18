package com.demo.eventbridge;

import java.time.Instant;
import java.util.UUID;

/** Format exchanged between the applications and the orchestrator. */
public record EventEnvelope(
        String id,
        String type,
        String source,
        String correlationId,
        Instant occurredAt,
        Object data) {

    public static EventEnvelope of(String source, IntegrationEvent event) {
        return new EventEnvelope(
                UUID.randomUUID().toString(),
                event.type(),
                source,
                event.correlationId(),
                Instant.now(),
                event);
    }
}
