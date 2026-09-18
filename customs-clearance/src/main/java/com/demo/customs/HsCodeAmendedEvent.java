package com.demo.customs;

import com.demo.eventbridge.IntegrationEvent;

public record HsCodeAmendedEvent(
        String invoice,
        String processId,
        int item,
        String previousHsCode,
        String newHsCode,
        String reason) implements IntegrationEvent {

    @Override
    public String type() {
        return "customs.hs-code-amended";
    }

    @Override
    public String correlationId() {
        return processId;
    }
}
