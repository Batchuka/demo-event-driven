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

@Service
public class CustomsService {

    private static final Logger log = LoggerFactory.getLogger(CustomsService.class);

    // Simulates the items of the invoice and the HS code the classification assigns to each one.
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

    public InvoiceClassification classify(String invoice, String processId) {
        var requested = new InvoiceClassification(invoice, processId, Status.CLASSIFYING, List.of(), Instant.now(),
                null);
        var stored = classifications.compute(invoice, (key, current) ->
                current != null && current.status() == Status.CLASSIFYING ? current : requested);
        if (stored != requested) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Invoice " + invoice + " is already being classified");
        }
        log.info("[customs] invoice {} received for classification ({} items) - process {}", invoice, GOODS.size(),
                processId);

        CompletableFuture.runAsync(() -> complete(invoice),
                CompletableFuture.delayedExecutor(timeToClassify.toMillis(), TimeUnit.MILLISECONDS));
        return requested;
    }

    public InvoiceClassification get(String invoice) {
        return Optional.ofNullable(classifications.get(invoice))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Invoice " + invoice + " was never submitted for classification"));
    }

    public InvoiceClassification amendHsCode(String invoice, int item, String newHsCode, String reason) {
        var previousHsCode = new AtomicReference<String>();
        var processId = new AtomicReference<String>();
        var updated = classifications.computeIfPresent(invoice, (key, current) -> {
            if (current.status() != Status.CLASSIFIED) {
                return current;
            }
            var items = current.items().stream().map(i -> {
                if (i.item() != item) {
                    return i;
                }
                previousHsCode.set(i.hsCode());
                return new ClassifiedItem(i.item(), i.description(), newHsCode);
            }).toList();
            processId.set(current.processId());
            return new InvoiceClassification(current.invoice(), current.processId(), current.status(), items,
                    current.requestedAt(), current.completedAt());
        });

        if (updated == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice " + invoice + " does not exist");
        }
        if (updated.status() != Status.CLASSIFIED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Invoice " + invoice + " is still being classified");
        }
        if (previousHsCode.get() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Invoice " + invoice + " has no item " + item);
        }

        log.info("[customs] invoice {} item {}: HS code amended from {} to {} ({})", invoice, item,
                previousHsCode.get(), newHsCode, reason);
        events.publishEvent(new HsCodeAmendedEvent(invoice, processId.get(), item, previousHsCode.get(), newHsCode,
                reason));
        return updated;
    }

    private void complete(String invoice) {
        var completed = classifications.computeIfPresent(invoice, (key, current) ->
                new InvoiceClassification(current.invoice(), current.processId(), Status.CLASSIFIED, GOODS,
                        current.requestedAt(), Instant.now()));
        log.info("[customs] invoice {} CLASSIFIED: {}", invoice,
                GOODS.stream().map(i -> i.description() + " -> " + i.hsCode()).toList());
        events.publishEvent(new GoodsClassifiedEvent(invoice, completed.processId(), GOODS));
    }
}
