package com.demo.orchestrator;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ImportProcess {

    public enum Status { IN_PROGRESS, COMPLETED, FAILED, INTERRUPTED }

    public record Step(Instant at, String description) {
    }

    public record Summary(
            String id,
            String container,
            String invoice,
            Status status,
            boolean paymentCompleted,
            boolean goodsClassified,
            Instant startedAt,
            Instant endedAt,
            List<Step> steps) {
    }

    private final String id;
    private final String container;
    private final String invoice;
    private final Instant startedAt = Instant.now();
    private final List<Step> steps = new ArrayList<>();
    private Status status = Status.IN_PROGRESS;
    private boolean paymentCompleted;
    private boolean goodsClassified;
    private Instant endedAt;

    ImportProcess(String id, String container, String invoice) {
        this.id = id;
        this.container = container;
        this.invoice = invoice;
    }

    public String id() {
        return id;
    }

    public synchronized Status status() {
        return status;
    }

    public synchronized void record(String description) {
        steps.add(new Step(Instant.now(), description));
    }

    /** @return true if this payment was the last thing missing to complete the process */
    public synchronized boolean recordPayment(String description) {
        paymentCompleted = true;
        record(description);
        return completeIfDone();
    }

    /** @return true if this classification was the last thing missing to complete the process */
    public synchronized boolean recordClassification(String description) {
        goodsClassified = true;
        record(description);
        return completeIfDone();
    }

    public synchronized void fail(String reason) {
        end(Status.FAILED, reason);
    }

    public synchronized void interrupt(String reason) {
        end(Status.INTERRUPTED, reason);
    }

    public synchronized Duration duration() {
        return Duration.between(startedAt, endedAt != null ? endedAt : Instant.now());
    }

    public synchronized Summary summary() {
        return new Summary(id, container, invoice, status, paymentCompleted, goodsClassified, startedAt, endedAt,
                List.copyOf(steps));
    }

    private boolean completeIfDone() {
        if (status != Status.IN_PROGRESS || !paymentCompleted || !goodsClassified) {
            return false;
        }
        end(Status.COMPLETED, "process completed");
        return true;
    }

    private void end(Status newStatus, String description) {
        if (status != Status.IN_PROGRESS) {
            return;
        }
        status = newStatus;
        endedAt = Instant.now();
        record(description);
    }
}
