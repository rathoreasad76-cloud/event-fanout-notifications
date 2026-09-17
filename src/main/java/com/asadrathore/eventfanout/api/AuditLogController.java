package com.asadrathore.eventfanout.api;

import com.asadrathore.eventfanout.infrastructure.persistence.AuditLogEntry;
import com.asadrathore.eventfanout.infrastructure.persistence.AuditLogRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AuditLogController {

    private final AuditLogRepository repository;

    public AuditLogController(AuditLogRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/api/audit-log")
    public List<AuditLogEntry> all() {
        return repository.findAllByOrderByReceivedAtDesc();
    }
}
