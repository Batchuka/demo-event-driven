package com.demo.orchestrator;

import java.math.BigDecimal;
import java.util.List;

/** Event types the workflow knows and the slice of each event's data that it uses. */
final class Events {

    static final String CARGO_ARRIVED = "logistics.cargo-arrived";
    static final String PAYMENT_COMPLETED = "finance.payment-completed";
    static final String PAYMENT_CANCELLED = "finance.payment-cancelled";
    static final String GOODS_CLASSIFIED = "customs.goods-classified";

    record CargoArrived(String container, String vessel, String port, String invoice, BigDecimal terminalCharges) {
    }

    record PaymentCompleted(String requestId, String payee, BigDecimal amount, String currency) {
    }

    record PaymentCancelled(String requestId, String reason) {
    }

    record GoodsClassified(String invoice, List<ClassifiedItem> items) {
    }

    record ClassifiedItem(int item, String description, String hsCode) {
    }

    private Events() {
    }
}
