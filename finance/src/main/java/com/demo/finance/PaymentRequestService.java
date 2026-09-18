package com.demo.finance;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
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

    public PaymentRequest open(String processId, String payee, BigDecimal amount, String currency,
                               String reference) {
        var now = Instant.now();
        var request = new PaymentRequest("PR-%04d".formatted(sequence.incrementAndGet()), processId, payee, amount,
                currency, reference, Status.OPEN, now, now);
        requests.put(request.id(), request);
        log.info("[finance] payment request {} opened: pay {} {} to {} ({}) - process {}", request.id(), currency,
                amount, payee, reference, processId);

        CompletableFuture.runAsync(() -> pay(request.id()),
                CompletableFuture.delayedExecutor(timeToPay.toMillis(), TimeUnit.MILLISECONDS));
        return request;
    }

    public PaymentRequest get(String id) {
        return Optional.ofNullable(requests.get(id))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Payment request " + id + " does not exist"));
    }

    public PaymentRequest cancel(String id, String reason) {
        var current = get(id);
        var cancelled = transition(id, Status.CANCELLED);
        if (cancelled == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Payment request " + id + " is " + get(id).status() + " and cannot be cancelled");
        }
        log.info("[finance] payment request {} cancelled: {}", id, reason);
        events.publishEvent(new PaymentCancelledEvent(id, current.processId(), reason));
        return cancelled;
    }

    private void pay(String id) {
        var paid = transition(id, Status.PAID);
        if (paid == null) {
            log.info("[finance] payment request {} is no longer open, payment skipped", id);
            return;
        }
        log.info("[finance] payment request {} PAID: {} {} to {}", id, paid.currency(), paid.amount(), paid.payee());
        events.publishEvent(new PaymentCompletedEvent(id, paid.processId(), paid.payee(), paid.amount(),
                paid.currency()));
    }

    /** Moves an OPEN request to the new status; returns null if it was not open. */
    private PaymentRequest transition(String id, Status newStatus) {
        var result = new AtomicReference<PaymentRequest>();
        requests.computeIfPresent(id, (key, current) -> {
            if (current.status() != Status.OPEN) {
                return current;
            }
            var updated = current.withStatus(newStatus);
            result.set(updated);
            return updated;
        });
        return result.get();
    }
}
