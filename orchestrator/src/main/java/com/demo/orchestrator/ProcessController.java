package com.demo.orchestrator;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.demo.orchestrator.ImportProcess.Summary;

@RestController
@RequestMapping("/processes")
public class ProcessController {

    private final ProcessRepository processes;

    public ProcessController(ProcessRepository processes) {
        this.processes = processes;
    }

    @GetMapping
    List<Summary> list() {
        return processes.list();
    }

    @GetMapping("/{invoice}")
    Summary get(@PathVariable String invoice) {
        return processes.find(invoice)
                .map(ImportProcess::summary)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "There is no process for invoice " + invoice));
    }
}
