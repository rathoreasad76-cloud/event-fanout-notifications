package com.asadrathore.eventfanout.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationLogRepository extends JpaRepository<NotificationLogEntry, Long> {
    boolean existsByMessageId(String messageId);

    List<NotificationLogEntry> findAllByOrderByNotifiedAtDesc();
}
