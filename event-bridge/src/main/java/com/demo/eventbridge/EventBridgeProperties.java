package com.demo.eventbridge;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param orchestratorUrl base URL of the orchestrator; the library stays off without it
 * @param source          name that identifies the application in the envelopes (default: spring.application.name)
 * @param attempts        how many times to try delivering an event before dropping it
 * @param interval        wait between attempts
 */
@ConfigurationProperties("event-bridge")
public record EventBridgeProperties(
        String orchestratorUrl,
        String source,
        @DefaultValue("5") int attempts,
        @DefaultValue("2s") Duration interval) {
}
