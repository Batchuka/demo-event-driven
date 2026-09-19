package com.demo.orchestrator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CustomsClient {

    private final RestClient customs;

    public CustomsClient(RestClient.Builder builder, @Value("${services.customs-url}") String url) {
        this.customs = builder.baseUrl(url).build();
    }

    /** Tells customs clearance it can classify whatever invoices it has pending. */
    public void classifyPending() {
        customs.post().uri("/customs/classify-pending").retrieve().toBodilessEntity();
    }
}
