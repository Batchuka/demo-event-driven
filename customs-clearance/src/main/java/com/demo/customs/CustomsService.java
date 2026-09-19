package com.demo.customs;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.demo.customs.InvoiceClassification.ClassifiedItem;
import com.demo.customs.InvoiceClassification.Status;
import com.demo.eventbridge.IntegrationEvent;

@Service
public class CustomsService {

    private static final Logger log = LoggerFactory.getLogger(CustomsService.class);

    // Simulates the items of an invoice and the HS code the classification assigns to each one.
    private static final List<ClassifiedItem> GOODS = List.of(
            new ClassifiedItem(1, "15-inch laptop", "8471.30.19"),
            new ClassifiedItem(2, "5G smartphone", "8517.13.00"),
            new ClassifiedItem(3, "Bluetooth headphones", "8518.30.00"));

    private final Map<String, InvoiceClassification> classifications = new ConcurrentHashMap<>();
    private final ApplicationEventPublisher events;
    private final Duration timeToClassify;

    public CustomsService(ApplicationEventPublisher events,
                          @Value("${app.simulation.delay}") Duration timeToClassify) {
        this.events = events;
        this.timeToClassify = timeToClassify;
    }

    public InvoiceClassification submit(String invoice) {
        var pending = new InvoiceClassification(invoice, Status.PENDING, List.of(), Instant.now(), null);
        var stored = classifications.compute(invoice, (key, current) ->
                current != null && current.status() != Status.CLASSIFIED ? current : pending);
        if (stored != pending) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Invoice " + invoice + " is already awaiting classification");
        }
        log.info("[customs] invoice {} submitted, pending classification", invoice);
        return pending;
    }

    public InvoiceClassification get(String invoice) {
        return Optional.ofNullable(classifications.get(invoice))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Invoice " + invoice + " was never submitted to customs"));
    }

    /** Looks for the invoices pending classification and classifies them; returns the ones it picked up. */
    public List<String> classifyPending() {
        var picked = classifications.keySet().stream().sorted()
                .filter(invoice -> transition(invoice, Status.PENDING, Status.CLASSIFYING) != null)
                .toList();
        if (picked.isEmpty()) {
            log.info("[customs] classification run: no invoices pending classification");
            return picked;
        }
        log.info("[customs] classification run: classifying {}", picked);
        picked.forEach(invoice -> CompletableFuture.runAsync(() -> complete(invoice),
                CompletableFuture.delayedExecutor(timeToClassify.toMillis(), TimeUnit.MILLISECONDS)));
        return picked;
    }

    private void complete(String invoice) {
        classifications.computeIfPresent(invoice, (key, current) -> new InvoiceClassification(invoice,
                Status.CLASSIFIED, GOODS, current.submittedAt(), Instant.now()));
        log.info("[customs] invoice {} CLASSIFIED: {}", invoice,
                GOODS.stream().map(i -> i.description() + " -> " + i.hsCode()).toList());
        events.publishEvent(IntegrationEvent.of("customs.goods-classified", invoice));
    }

    /** Moves an invoice from one status to the next; returns null if it was not in the expected status. */
    private InvoiceClassification transition(String invoice, Status from, Status to) {
        var result = new AtomicReference<InvoiceClassification>();
        classifications.computeIfPresent(invoice, (key, current) -> {
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
