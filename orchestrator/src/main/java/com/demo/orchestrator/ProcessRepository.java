package com.demo.orchestrator;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class ProcessRepository {

    private final Map<String, ImportProcess> processes = new ConcurrentHashMap<>();

    public ImportProcess start(String container, String invoice) {
        var process = new ImportProcess("IMP-" + UUID.randomUUID().toString().substring(0, 6), container, invoice);
        processes.put(process.id(), process);
        return process;
    }

    public Optional<ImportProcess> find(String id) {
        return Optional.ofNullable(processes.get(id));
    }

    public List<ImportProcess.Summary> list() {
        return processes.values().stream()
                .map(ImportProcess::summary)
                .sorted(Comparator.comparing(ImportProcess.Summary::startedAt).reversed())
                .toList();
    }
}
