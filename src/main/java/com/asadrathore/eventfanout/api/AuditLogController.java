package com.asadrathore.eventfanout.api;

import com.asadrathore.eventfanout.infrastructure.persistence.AuditLogEntry;
import com.asadrathore.eventfanout.infrastructure.persistence.AuditLogRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "Audit log", description = "What the unfiltered audit consumer recorded")
public class AuditLogController {

    private final AuditLogRepository repository;

    public AuditLogController(AuditLogRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/api/audit-log")
    @Operation(summary = "List audit log entries", description = "Every event the audit consumer received, newest first.")
    public List<AuditLogEntry> all() {
        return repository.findAllByOrderByReceivedAtDesc();
    }
}
