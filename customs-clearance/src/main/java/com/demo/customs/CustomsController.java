package com.demo.customs;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/customs")
public class CustomsController {

    public record SubmitInvoice(@NotBlank String invoice) {
    }

    public record ClassificationRun(List<String> invoices) {
    }

    private final CustomsService service;

    public CustomsController(CustomsService service) {
        this.service = service;
    }

    @PostMapping("/invoices")
    ResponseEntity<InvoiceClassification> submit(@Valid @RequestBody SubmitInvoice body) {
        var classification = service.submit(body.invoice());
        return ResponseEntity.created(URI.create("/customs/invoices/" + classification.invoice()))
                .body(classification);
    }

    @PostMapping("/classify-pending")
    @ResponseStatus(HttpStatus.ACCEPTED)
    ClassificationRun classifyPending() {
        return new ClassificationRun(service.classifyPending());
    }

    @GetMapping("/invoices/{invoice}")
    InvoiceClassification get(@PathVariable String invoice) {
        return service.get(invoice);
    }
}
