package com.demo.customs;

import java.util.List;

import com.demo.customs.InvoiceClassification.ClassifiedItem;
import com.demo.eventbridge.IntegrationEvent;

public record GoodsClassifiedEvent(
        String invoice,
        String processId,
        List<ClassifiedItem> items) implements IntegrationEvent {

    @Override
    public String type() {
        return "customs.goods-classified";
    }

    @Override
    public String correlationId() {
        return processId;
    }
}
