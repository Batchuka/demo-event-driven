package com.demo.customs;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

@RestController
@RequestMapping("/customs/invoices/{invoice}")
public class CustomsController {

    public record ClassificationRequest(String processId) {
    }

    public record HsCodeAmendment(
            @Positive int item,
            @NotBlank @Pattern(regexp = "\\d{4}\\.\\d{2}\\.\\d{2}", message = "HS code must follow the format 0000.00.00") String hsCode,
            @NotBlank String reason) {
    }

    private final CustomsService service;

    public CustomsController(CustomsService service) {
        this.service = service;
    }

    @PostMapping("/classify")
    @ResponseStatus(HttpStatus.ACCEPTED)
    InvoiceClassification classify(@PathVariable String invoice, @RequestBody(required = false) ClassificationRequest body) {
        return service.classify(invoice, body == null ? null : body.processId());
    }

    @PostMapping("/amend-hs-code")
    InvoiceClassification amendHsCode(@PathVariable String invoice, @Valid @RequestBody HsCodeAmendment body) {
        return service.amendHsCode(invoice, body.item(), body.hsCode(), body.reason());
    }

    @GetMapping("/classification")
    InvoiceClassification get(@PathVariable String invoice) {
        return service.get(invoice);
    }
}
