package com.asadrathore.eventfanout.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLogEntry, Long> {
    boolean existsByMessageId(String messageId);

    List<AuditLogEntry> findAllByOrderByReceivedAtDesc();
}
