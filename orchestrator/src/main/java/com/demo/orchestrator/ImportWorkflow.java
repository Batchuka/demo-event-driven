package com.demo.orchestrator;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.demo.eventbridge.EventEnvelope;
import com.demo.orchestrator.ImportProcess.Status;

/**
 * Workflow of the import process. The orchestrator does not tell the services what to do or hand them any data: each
 * one owns its work and its data. It only listens to events and lets the next services know they can act.
 * <ol>
 *   <li>logistics.cargo-arrived: opens the process and, in parallel, tells customs clearance to classify its
 *       pending invoices and finance to pay its pending payment requests;</li>
 *   <li>finance.payment-completed and customs.goods-classified: record each step;</li>
 *   <li>when both steps are done, the process is completed.</li>
 * </ol>
 * The events of one process are tied together by their correlation id, which is the invoice number.
 */
@Component
public class ImportWorkflow implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ImportWorkflow.class);

    private final ProcessRepository processes;
    private final FinanceClient finance;
    private final CustomsClient customs;
    private final Set<String> handledEvents = ConcurrentHashMap.newKeySet();
    private final ExecutorService inParallel = Executors.newVirtualThreadPerTaskExecutor();

    public ImportWorkflow(ProcessRepository processes, FinanceClient finance, CustomsClient customs) {
        this.processes = processes;
        this.finance = finance;
        this.customs = customs;
    }

    @Async
    public void onEvent(EventEnvelope event) {
        if (!handledEvents.add(event.id())) {
            log.info("<< event {} ({}) was already handled, repeated delivery ignored", event.type(), event.id());
            return;
        }
        log.info("<< event received: {} (source: {}, invoice: {})", event.type(), event.source(),
                event.correlationId() != null ? event.correlationId() : "none");
        try {
            switch (event.type()) {
                case Events.CARGO_ARRIVED -> onCargoArrived(event);
                case Events.PAYMENT_COMPLETED -> onPaymentCompleted(event);
                case Events.GOODS_CLASSIFIED -> onGoodsClassified(event);
                default -> log.info("   no workflow step reacts to {}", event.type());
            }
        } catch (RuntimeException e) {
            log.error("   error handling event {}: {}", event.type(), e.toString());
        }
    }

    private void onCargoArrived(EventEnvelope event) {
        if (event.correlationId() == null) {
            log.warn("   event {} carries no invoice to correlate the process with, ignored", event.type());
            return;
        }
        var process = processes.start(event.correlationId());
        var id = process.invoice();
        log.info("[{}] NEW IMPORT PROCESS: the cargo arrived", id);
        process.record("cargo arrived");
        log.info("[{}] workflow: cargo arrived => customs clearance can classify and finance can pay, in parallel", id);

        var classification = CompletableFuture.runAsync(() -> letCustomsClassify(process), inParallel);
        var payment = CompletableFuture.runAsync(() -> letFinancePay(process), inParallel);
        CompletableFuture.allOf(classification, payment).join();

        if (process.status() == Status.IN_PROGRESS) {
            log.info("[{}] services informed, waiting for the finance and customs events", id);
        }
    }

    private void letCustomsClassify(ImportProcess process) {
        try {
            log.info("[{}] -> customs-clearance: you can classify your pending invoices", process.invoice());
            customs.classifyPending();
            log.info("[{}] <- customs-clearance accepted", process.invoice());
            process.record("customs clearance informed");
        } catch (RuntimeException e) {
            fail(process, "customs-clearance did not accept the request: " + reason(e));
        }
    }

    private void letFinancePay(ImportProcess process) {
        try {
            log.info("[{}] -> finance: you can pay your pending payment requests", process.invoice());
            finance.payPending();
            log.info("[{}] <- finance accepted", process.invoice());
            process.record("finance informed");
        } catch (RuntimeException e) {
            fail(process, "finance did not accept the request: " + reason(e));
        }
    }

    private void onPaymentCompleted(EventEnvelope event) {
        processOf(event).ifPresent(process -> {
            log.info("[{}] payment completed", process.invoice());
            var completed = process.recordPayment("payment completed");
            advance(process, completed, "the goods classification");
        });
    }

    private void onGoodsClassified(EventEnvelope event) {
        processOf(event).ifPresent(process -> {
            log.info("[{}] goods classified", process.invoice());
            var completed = process.recordClassification("goods classified");
            advance(process, completed, "the payment");
        });
    }

    private void advance(ImportProcess process, boolean completed, String pending) {
        if (completed) {
            log.info("[{}] ============================================================", process.invoice());
            log.info("[{}] IMPORT PROCESS COMPLETED in {}s: payment completed and goods classified",
                    process.invoice(), String.format(Locale.ROOT, "%.1f", process.duration().toMillis() / 1000.0));
            log.info("[{}] ============================================================", process.invoice());
        } else if (process.status() == Status.IN_PROGRESS) {
            log.info("[{}] waiting for {}", process.invoice(), pending);
        }
    }

    private void fail(ImportProcess process, String reason) {
        log.error("[{}] {}", process.invoice(), reason);
        process.fail(reason);
    }

    private static String reason(Throwable error) {
        var cause = NestedExceptionUtils.getMostSpecificCause(error);
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }

    private Optional<ImportProcess> processOf(EventEnvelope event) {
        var process = event.correlationId() == null ? Optional.<ImportProcess>empty()
                : processes.find(event.correlationId());
        if (process.isEmpty()) {
            log.warn("   event {} does not belong to any known process, ignored", event.type());
        }
        return process;
    }

    @Override
    public void close() {
        inParallel.shutdown();
    }
}
