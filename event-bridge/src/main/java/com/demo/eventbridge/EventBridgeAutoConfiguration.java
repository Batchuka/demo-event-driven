package com.demo.eventbridge;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;

@AutoConfiguration
@ConditionalOnProperty("event-bridge.orchestrator-url")
@EnableConfigurationProperties(EventBridgeProperties.class)
public class EventBridgeAutoConfiguration {

    @Bean
    EventBridgePublisher eventBridgePublisher(EventBridgeProperties properties, RestClient.Builder builder,
                                              Environment environment) {
        var orchestrator = builder.baseUrl(properties.orchestratorUrl()).build();
        var source = properties.source() != null
                ? properties.source()
                : environment.getProperty("spring.application.name", "unknown");
        return new EventBridgePublisher(orchestrator, properties, source);
    }
}
