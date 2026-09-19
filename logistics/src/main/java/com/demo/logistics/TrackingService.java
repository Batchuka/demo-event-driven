package com.demo.logistics;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.demo.eventbridge.IntegrationEvent;
import com.demo.logistics.OceanCargo.Occurrence;
import com.demo.logistics.OceanCargo.Status;

@Service
public class TrackingService {

    private static final Logger log = LoggerFactory.getLogger(TrackingService.class);

    private final Map<String, OceanCargo> cargos = new ConcurrentHashMap<>();
    private final ApplicationEventPublisher events;

    public TrackingService(ApplicationEventPublisher events) {
        this.events = events;
    }

    public OceanCargo get(String container) {
        return Optional.ofNullable(cargos.get(container))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Container " + container + " is not being tracked"));
    }

    public OceanCargo registerShipment(
        String container, String vessel, String originPort, String destinationPort, String invoice, LocalDate estimatedArrival
    ) {
        var cargo = new OceanCargo(
            container, vessel, originPort, destinationPort, invoice, estimatedArrival, Status.IN_TRANSIT,
            List.of(new Occurrence(Instant.now(), "Loaded at " + originPort + " on vessel " + vessel))
        );
        if (cargos.putIfAbsent(container, cargo) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Container " + container + " is already being tracked");
        }
        log.info("[logistics] shipment registered: container {} on vessel {} ({} -> {}), invoice {}", container, vessel, originPort, destinationPort, invoice);
        return cargo;
    }

    public OceanCargo registerArrival(String container, String port) {
        var description = "Arrival confirmed by the " + port + " terminal";
        var cargo = cargos.computeIfPresent(container, (key, current) -> current.withOccurrence(Status.ARRIVED, description));
        if (cargo == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Container " + container + " is not being tracked");
        }
        log.info("[logistics] terminal webhook: container {} ARRIVED at {} (invoice {})", container, port, cargo.invoice());
        
        events.publishEvent(IntegrationEvent.of("logistics.cargo-arrived", cargo.invoice()));
        return cargo;
    }
}
