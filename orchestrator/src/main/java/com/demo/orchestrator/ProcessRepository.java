package com.demo.orchestrator;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class ProcessRepository {

    private final Map<String, ImportProcess> processes = new ConcurrentHashMap<>();

    public ImportProcess start(String invoice) {
        var process = new ImportProcess(invoice);
        processes.put(invoice, process);
        return process;
    }

    public Optional<ImportProcess> find(String invoice) {
        return Optional.ofNullable(processes.get(invoice));
    }

    public List<ImportProcess.Summary> list() {
        return processes.values().stream()
                .map(ImportProcess::summary)
                .sorted(Comparator.comparing(ImportProcess.Summary::startedAt).reversed())
                .toList();
    }
}
