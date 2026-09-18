package com.demo.orchestrator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CustomsClient {

    private record ClassificationRequestBody(String processId) {
    }

    public record ClassificationRequested(String invoice, String status) {
    }

    private final RestClient customs;

    public CustomsClient(RestClient.Builder builder, @Value("${services.customs-url}") String url) {
        this.customs = builder.baseUrl(url).build();
    }

    public ClassificationRequested classifyGoods(String invoice, String processId) {
        return customs.post()
                .uri("/customs/invoices/{invoice}/classify", invoice)
                .body(new ClassificationRequestBody(processId))
                .retrieve()
                .body(ClassificationRequested.class);
    }
}
