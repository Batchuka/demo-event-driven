package com.demo.finance;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.demo.eventbridge.IntegrationEvent;
import com.demo.finance.PaymentRequest.Status;

@Service
public class PaymentRequestService {

    private static final Logger log = LoggerFactory.getLogger(PaymentRequestService.class);

    private final Map<String, PaymentRequest> requests = new ConcurrentHashMap<>();
    private final AtomicInteger sequence = new AtomicInteger();
    private final ApplicationEventPublisher events;
    private final Duration timeToPay;

    public PaymentRequestService(ApplicationEventPublisher events,
                                 @Value("${app.simulation.delay}") Duration timeToPay) {
        this.events = events;
        this.timeToPay = timeToPay;
    }

    public PaymentRequest open(String invoice, String payee, BigDecimal amount, String currency) {
        var now = Instant.now();
        var request = new PaymentRequest("PR-%04d".formatted(sequence.incrementAndGet()), invoice, payee, amount,
                currency, Status.PENDING, now, now);
        requests.put(request.id(), request);
        log.info("[finance] payment request {} opened: {} {} to {} (invoice {}), pending payment", request.id(),
                currency, amount, payee, invoice);
        return request;
    }

    public PaymentRequest get(String id) {
        return Optional.ofNullable(requests.get(id))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Payment request " + id + " does not exist"));
    }

    /** Looks for the pending payment requests and pays them; returns the ids it picked up. */
    public List<String> payPending() {
        var picked = requests.keySet().stream().sorted()
                .filter(id -> transition(id, Status.PENDING, Status.PROCESSING) != null)
                .toList();
        if (picked.isEmpty()) {
            log.info("[finance] payment run: no pending payment requests");
            return picked;
        }
        log.info("[finance] payment run: paying {}", picked);
        picked.forEach(id -> CompletableFuture.runAsync(() -> pay(id),
                CompletableFuture.delayedExecutor(timeToPay.toMillis(), TimeUnit.MILLISECONDS)));
        return picked;
    }

    private void pay(String id) {
        var paid = transition(id, Status.PROCESSING, Status.PAID);
        log.info("[finance] payment request {} PAID: {} {} to {} (invoice {})", id, paid.currency(), paid.amount(),
                paid.payee(), paid.invoice());
        events.publishEvent(IntegrationEvent.of("finance.payment-completed", paid.invoice()));
    }

    /** Moves a request from one status to the next; returns null if it was not in the expected status. */
    private PaymentRequest transition(String id, Status from, Status to) {
        var result = new AtomicReference<PaymentRequest>();
        requests.computeIfPresent(id, (key, current) -> {
            if (current.status() != from) {
                return current;
            }
            var updated = current.withStatus(to);
            result.set(updated);
            return updated;
        });
        return result.get();
    }
}
