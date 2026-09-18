package com.demo.eventbridge;

/**
 * Marks a Spring application event as an integration event: every event published through
 * {@code ApplicationEventPublisher} that implements this interface is forwarded to the orchestrator.
 */
public interface IntegrationEvent {

    String type();

    /** Identifier of the process the event belongs to, when there is one already. */
    default String correlationId() {
        return null;
    }
}
