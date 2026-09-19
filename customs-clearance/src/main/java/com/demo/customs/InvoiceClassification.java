package com.demo.customs;

import java.time.Instant;
import java.util.List;

public record InvoiceClassification(
        String invoice,
        Status status,
        List<ClassifiedItem> items,
        Instant submittedAt,
        Instant classifiedAt) {

    public enum Status { PENDING, CLASSIFYING, CLASSIFIED }

    public record ClassifiedItem(int item, String description, String hsCode) {
    }

    InvoiceClassification withStatus(Status newStatus) {
        return new InvoiceClassification(invoice, newStatus, items, submittedAt, classifiedAt);
    }
}
