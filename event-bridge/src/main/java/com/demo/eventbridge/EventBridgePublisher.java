package com.demo.eventbridge;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.web.client.RestClient;

public class EventBridgePublisher implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(EventBridgePublisher.class);

    private final RestClient orchestrator;
    private final EventBridgeProperties properties;
    private final String source;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public EventBridgePublisher(RestClient orchestrator, EventBridgeProperties properties, String source) {
        this.orchestrator = orchestrator;
        this.properties = properties;
        this.source = source;
    }

    @EventListener
    public void onIntegrationEvent(IntegrationEvent event) {
        var envelope = EventEnvelope.of(source, event);
        log.info("[event-bridge] internal event {} captured, sending it to the orchestrator", envelope.type());
        executor.execute(() -> deliver(envelope));
    }

    private void deliver(EventEnvelope envelope) {
        for (int attempt = 1; attempt <= properties.attempts(); attempt++) {
            try {
                orchestrator.post().uri("/events").body(envelope).retrieve().toBodilessEntity();
                log.info("[event-bridge] event {} delivered to the orchestrator", envelope.type());
                return;
            } catch (RuntimeException e) {
                log.warn("[event-bridge] failed to deliver {} (attempt {}/{}): {}",
                        envelope.type(), attempt, properties.attempts(), reason(e));
                if (attempt < properties.attempts() && !waitBeforeRetry()) {
                    return;
                }
            }
        }
        log.error("[event-bridge] event {} dropped after {} attempts", envelope.type(), properties.attempts());
    }

    private static String reason(Throwable error) {
        var cause = NestedExceptionUtils.getMostSpecificCause(error);
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }

    private boolean waitBeforeRetry() {
        try {
            Thread.sleep(properties.interval());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public void close() {
        executor.shutdown();
    }
}
