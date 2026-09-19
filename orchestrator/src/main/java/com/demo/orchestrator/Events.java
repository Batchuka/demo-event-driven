package com.demo.orchestrator;

/** Names of the events the workflow reacts to. */
final class Events {

    static final String CARGO_ARRIVED = "logistics.cargo-arrived";
    static final String PAYMENT_COMPLETED = "finance.payment-completed";
    static final String GOODS_CLASSIFIED = "customs.goods-classified";

    private Events() {
    }
}
