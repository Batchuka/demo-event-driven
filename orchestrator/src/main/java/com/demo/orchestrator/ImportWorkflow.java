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
import com.demo.orchestrator.Events.CargoArrived;
import com.demo.orchestrator.Events.GoodsClassified;
import com.demo.orchestrator.Events.PaymentCancelled;
import com.demo.orchestrator.Events.PaymentCompleted;
import com.demo.orchestrator.ImportProcess.Status;

import tools.jackson.databind.json.JsonMapper;

/**
 * Workflow of the import process:
 * <ol>
 *   <li>logistics.cargo-arrived: opens the process and, in parallel, asks customs clearance to classify the
 *       goods and finance to open the payment of the terminal charges;</li>
 *   <li>finance.payment-completed and customs.goods-classified: record each step;</li>
 *   <li>when both steps are done, the process is completed.</li>
 * </ol>
 */
@Component
public class ImportWorkflow implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ImportWorkflow.class);

    private final ProcessRepository processes;
    private final FinanceClient finance;
    private final CustomsClient customs;
    private final JsonMapper json;
    private final Set<String> handledEvents = ConcurrentHashMap.newKeySet();
    private final ExecutorService inParallel = Executors.newVirtualThreadPerTaskExecutor();

    public ImportWorkflow(ProcessRepository processes, FinanceClient finance, CustomsClient customs,
                          JsonMapper json) {
        this.processes = processes;
        this.finance = finance;
        this.customs = customs;
        this.json = json;
    }

    @Async
    public void onEvent(EventEnvelope event) {
        if (!handledEvents.add(event.id())) {
            log.info("<< event {} ({}) was already handled, repeated delivery ignored", event.type(), event.id());
            return;
        }
        log.info("<< event received: {} (source: {}, process: {})", event.type(), event.source(),
                event.correlationId() != null ? event.correlationId() : "none yet");
        try {
            switch (event.type()) {
                case Events.CARGO_ARRIVED -> onCargoArrived(data(event, CargoArrived.class));
                case Events.PAYMENT_COMPLETED -> onPaymentCompleted(event, data(event, PaymentCompleted.class));
                case Events.PAYMENT_CANCELLED -> onPaymentCancelled(event, data(event, PaymentCancelled.class));
                case Events.GOODS_CLASSIFIED -> onGoodsClassified(event, data(event, GoodsClassified.class));
                default -> log.info("   no workflow step reacts to {}", event.type());
            }
        } catch (RuntimeException e) {
            log.error("   error handling event {}: {}", event.type(), e.toString());
        }
    }

    private void onCargoArrived(CargoArrived cargo) {
        var process = processes.start(cargo.container(), cargo.invoice());
        var id = process.id();
        log.info("[{}] NEW IMPORT PROCESS: container {} (vessel {}) arrived at {}, invoice {}", id,
                cargo.container(), cargo.vessel(), cargo.port(), cargo.invoice());
        process.record("cargo arrived at " + cargo.port());
        log.info("[{}] workflow: cargo arrived => classify the goods and open the terminal charges payment, in parallel",
                id);

        var classification = CompletableFuture.runAsync(() -> requestClassification(process, cargo), inParallel);
        var payment = CompletableFuture.runAsync(() -> requestPayment(process, cargo), inParallel);
        CompletableFuture.allOf(classification, payment).join();

        if (process.status() == Status.IN_PROGRESS) {
            log.info("[{}] requests sent, waiting for the finance and customs events", id);
        }
    }

    private void requestClassification(ImportProcess process, CargoArrived cargo) {
        try {
            log.info("[{}] -> customs-clearance: classify the goods of invoice {}", process.id(), cargo.invoice());
            var response = customs.classifyGoods(cargo.invoice(), process.id());
            log.info("[{}] <- customs-clearance accepted: invoice {} {}", process.id(), response.invoice(),
                    response.status());
            process.record("goods classification requested from customs clearance");
        } catch (RuntimeException e) {
            fail(process, "customs-clearance did not accept the classification request: " + reason(e));
        }
    }

    private void requestPayment(ImportProcess process, CargoArrived cargo) {
        try {
            var payee = cargo.port() + " Terminal";
            log.info("[{}] -> finance: open a payment request of {} to {}", process.id(), cargo.terminalCharges(),
                    payee);
            var response = finance.openPaymentRequest(process.id(), payee, cargo.terminalCharges(), "BRL",
                    "Terminal charges for container " + cargo.container());
            log.info("[{}] <- finance accepted: payment request {} {}", process.id(), response.id(),
                    response.status());
            process.record("terminal charges payment requested from finance (" + response.id() + ")");
        } catch (RuntimeException e) {
            fail(process, "finance did not accept the payment request: " + reason(e));
        }
    }

    private void onPaymentCompleted(EventEnvelope event, PaymentCompleted payment) {
        processOf(event).ifPresent(process -> {
            log.info("[{}] payment {} completed: {} {} to {}", process.id(), payment.requestId(),
                    payment.currency(), payment.amount(), payment.payee());
            var completed = process.recordPayment("payment " + payment.requestId() + " completed");
            advance(process, completed, "the goods classification");
        });
    }

    private void onGoodsClassified(EventEnvelope event, GoodsClassified classification) {
        processOf(event).ifPresent(process -> {
            log.info("[{}] goods of invoice {} classified ({} items)", process.id(), classification.invoice(),
                    classification.items().size());
            var completed = process.recordClassification("goods of invoice " + classification.invoice()
                    + " classified");
            advance(process, completed, "the payment of the terminal charges");
        });
    }

    private void onPaymentCancelled(EventEnvelope event, PaymentCancelled cancellation) {
        processOf(event).ifPresent(process -> {
            log.warn("[{}] payment {} cancelled ({}), process interrupted", process.id(),
                    cancellation.requestId(), cancellation.reason());
            process.interrupt("payment " + cancellation.requestId() + " cancelled: " + cancellation.reason());
        });
    }

    private void advance(ImportProcess process, boolean completed, String pending) {
        if (completed) {
            log.info("[{}] ============================================================", process.id());
            log.info("[{}] IMPORT PROCESS COMPLETED in {}s: payment completed and goods classified", process.id(),
                    String.format(Locale.ROOT, "%.1f", process.duration().toMillis() / 1000.0));
            log.info("[{}] ============================================================", process.id());
        } else if (process.status() == Status.IN_PROGRESS) {
            log.info("[{}] waiting for {}", process.id(), pending);
        }
    }

    private void fail(ImportProcess process, String reason) {
        log.error("[{}] {}", process.id(), reason);
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

    private <T> T data(EventEnvelope event, Class<T> type) {
        return json.convertValue(event.data(), type);
    }

    @Override
    public void close() {
        inParallel.shutdown();
    }
}
