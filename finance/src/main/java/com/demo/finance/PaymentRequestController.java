package com.demo.finance;

import java.math.BigDecimal;
import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@RestController
@RequestMapping("/payment-requests")
public class PaymentRequestController {

    public record OpenPaymentRequest(
            String processId,
            @NotBlank String payee,
            @NotNull @Positive BigDecimal amount,
            @NotBlank String currency,
            String reference) {
    }

    public record CancelPaymentRequest(@NotBlank String reason) {
    }

    private final PaymentRequestService service;

    public PaymentRequestController(PaymentRequestService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<PaymentRequest> open(@Valid @RequestBody OpenPaymentRequest body) {
        var request = service.open(body.processId(), body.payee(), body.amount(), body.currency(), body.reference());
        return ResponseEntity.accepted().location(URI.create("/payment-requests/" + request.id())).body(request);
    }

    @PostMapping("/{id}/cancellation")
    PaymentRequest cancel(@PathVariable String id, @Valid @RequestBody CancelPaymentRequest body) {
        return service.cancel(id, body.reason());
    }

    @GetMapping("/{id}")
    PaymentRequest get(@PathVariable String id) {
        return service.get(id);
    }
}
