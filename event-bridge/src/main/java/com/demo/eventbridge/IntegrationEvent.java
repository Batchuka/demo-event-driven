package com.demo.eventbridge;

/**
 * Publish it with {@code ApplicationEventPublisher} and the library forwards it to the orchestrator.
 *
 * @param type          name of the event, which is what the orchestrator waits for
 * @param correlationId business key that ties the event to the process it belongs to (e.g. an invoice number)
 * @param data          optional payload: any object that serializes to JSON
 */
public record IntegrationEvent(String type, String correlationId, Object data) {

    public static IntegrationEvent of(String type) {
        return new IntegrationEvent(type, null, null);
    }

    public static IntegrationEvent of(String type, String correlationId) {
        return new IntegrationEvent(type, correlationId, null);
    }

    public static IntegrationEvent of(String type, String correlationId, Object data) {
        return new IntegrationEvent(type, correlationId, data);
    }
}
