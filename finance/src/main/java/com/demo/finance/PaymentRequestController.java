package com.demo.finance;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@RestController
public class PaymentRequestController {

    public record OpenPaymentRequest(
            @NotBlank String invoice,
            @NotBlank String payee,
            @NotNull @Positive BigDecimal amount,
            @NotBlank String currency) {
    }

    public record PaymentRun(List<String> requests) {
    }

    private final PaymentRequestService service;

    public PaymentRequestController(PaymentRequestService service) {
        this.service = service;
    }

    @PostMapping("/payment-requests")
    ResponseEntity<PaymentRequest> open(@Valid @RequestBody OpenPaymentRequest body) {
        var request = service.open(body.invoice(), body.payee(), body.amount(), body.currency());
        return ResponseEntity.created(URI.create("/payment-requests/" + request.id())).body(request);
    }

    @PostMapping("/payment-runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    PaymentRun run() {
        return new PaymentRun(service.payPending());
    }

    @GetMapping("/payment-requests/{id}")
    PaymentRequest get(@PathVariable String id) {
        return service.get(id);
    }
}
