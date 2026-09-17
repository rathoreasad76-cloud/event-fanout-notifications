package com.asadrathore.eventfanout.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FailedNotificationRepository extends JpaRepository<FailedNotificationEntry, Long> {
    boolean existsByMessageId(String messageId);

    List<FailedNotificationEntry> findAllByOrderByMovedToDlqAtDesc();
}
