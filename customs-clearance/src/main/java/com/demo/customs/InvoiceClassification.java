package com.demo.customs;

import java.time.Instant;
import java.util.List;

public record InvoiceClassification(
        String invoice,
        String processId,
        Status status,
        List<ClassifiedItem> items,
        Instant requestedAt,
        Instant completedAt) {

    public enum Status { CLASSIFYING, CLASSIFIED }

    public record ClassifiedItem(int item, String description, String hsCode) {
    }
}
