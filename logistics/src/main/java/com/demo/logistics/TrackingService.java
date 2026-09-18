package com.demo.logistics;

import java.math.BigDecimal;
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

    public OceanCargo registerShipment(String container, String vessel, String originPort, String destinationPort,
                                       String invoice, LocalDate estimatedArrival) {
        var cargo = new OceanCargo(container, vessel, originPort, destinationPort, invoice, estimatedArrival,
                Status.IN_TRANSIT,
                List.of(new Occurrence(Instant.now(), "Loaded at " + originPort + " on vessel " + vessel)));
        if (cargos.putIfAbsent(container, cargo) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Container " + container + " is already being tracked");
        }
        log.info("[logistics] shipment registered: container {} on vessel {} ({} -> {}), invoice {}", container,
                vessel, originPort, destinationPort, invoice);
        return cargo;
    }

    public OceanCargo get(String container) {
        return Optional.ofNullable(cargos.get(container))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Container " + container + " is not being tracked"));
    }

    /** Handles the terminal notice: updates the tracking (creating it if needed) and publishes the internal event. */
    public OceanCargo registerArrival(String container, String vessel, String port, String invoice,
                                      BigDecimal terminalCharges) {
        var description = "Arrival confirmed by the " + port + " terminal";
        var cargo = cargos.compute(container, (key, current) -> current != null
                ? current.withOccurrence(Status.ARRIVED, description)
                : new OceanCargo(container, vessel, null, port, invoice, null, Status.ARRIVED,
                        List.of(new Occurrence(Instant.now(), description))));
        log.info("[logistics] terminal webhook: container {} ARRIVED at {} (vessel {}, invoice {})", container, port,
                vessel, invoice);

        events.publishEvent(new CargoArrivedEvent(container, vessel, port, invoice, terminalCharges, Instant.now()));
        return cargo;
    }
}
