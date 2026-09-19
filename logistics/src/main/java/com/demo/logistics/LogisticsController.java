package com.demo.logistics;

import java.net.URI;
import java.time.LocalDate;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
public class LogisticsController {

    public record Shipment(
            @NotBlank String container,
            @NotBlank String vessel,
            @NotBlank String originPort,
            @NotBlank String destinationPort,
            @NotBlank String invoice,
            LocalDate estimatedArrival) {
    }

    public record TerminalArrivalNotice(
            @NotBlank String container,
            @NotBlank String port) {
    }

    private final TrackingService service;

    public LogisticsController(TrackingService service) {
        this.service = service;
    }

    @PostMapping("/shipments")
    ResponseEntity<OceanCargo> registerShipment(@Valid @RequestBody Shipment shipment) {
        var cargo = service.registerShipment(shipment.container(), shipment.vessel(), shipment.originPort(),
                shipment.destinationPort(), shipment.invoice(), shipment.estimatedArrival());
        return ResponseEntity.created(URI.create("/tracking/" + cargo.container())).body(cargo);
    }

    @PostMapping("/webhooks/terminal/cargo-arrival")
    @ResponseStatus(HttpStatus.ACCEPTED)
    OceanCargo cargoArrival(@Valid @RequestBody TerminalArrivalNotice notice) {
        return service.registerArrival(notice.container(), notice.port());
    }

    @GetMapping("/tracking/{container}")
    OceanCargo track(@PathVariable String container) {
        return service.get(container);
    }
}
