package com.demo.orchestrator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class FinanceClient {

    private final RestClient finance;

    public FinanceClient(RestClient.Builder builder, @Value("${services.finance-url}") String url) {
        this.finance = builder.baseUrl(url).build();
    }

    /** Tells finance it can pay whatever payment requests it has pending. */
    public void payPending() {
        finance.post().uri("/payment-runs").retrieve().toBodilessEntity();
    }
}
