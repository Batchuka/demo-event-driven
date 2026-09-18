package com.demo.orchestrator;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class FinanceClient {

    private record PaymentRequestBody(String processId, String payee, BigDecimal amount, String currency,
                                      String reference) {
    }

    public record PaymentRequestOpened(String id, String status) {
    }

    private final RestClient finance;

    public FinanceClient(RestClient.Builder builder, @Value("${services.finance-url}") String url) {
        this.finance = builder.baseUrl(url).build();
    }

    public PaymentRequestOpened openPaymentRequest(String processId, String payee, BigDecimal amount,
                                                   String currency, String reference) {
        return finance.post()
                .uri("/payment-requests")
                .body(new PaymentRequestBody(processId, payee, amount, currency, reference))
                .retrieve()
                .body(PaymentRequestOpened.class);
    }
}
