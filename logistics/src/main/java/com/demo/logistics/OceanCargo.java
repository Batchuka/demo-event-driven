package com.demo.logistics;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public record OceanCargo(
        String container,
        String vessel,
        String originPort,
        String destinationPort,
        String invoice,
        LocalDate estimatedArrival,
        Status status,
        List<Occurrence> history) {

    public enum Status { IN_TRANSIT, ARRIVED }

    public record Occurrence(Instant at, String description) {
    }

    OceanCargo withOccurrence(Status newStatus, String description) {
        var newHistory = new ArrayList<>(history);
        newHistory.add(new Occurrence(Instant.now(), description));
        return new OceanCargo(container, vessel, originPort, destinationPort, invoice, estimatedArrival, newStatus,
                List.copyOf(newHistory));
    }
}
