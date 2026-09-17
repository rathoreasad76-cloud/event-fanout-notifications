package com.asadrathore.eventfanout.api;

import com.asadrathore.eventfanout.infrastructure.persistence.FailedNotificationEntry;
import com.asadrathore.eventfanout.infrastructure.persistence.FailedNotificationRepository;
import com.asadrathore.eventfanout.infrastructure.persistence.NotificationLogEntry;
import com.asadrathore.eventfanout.infrastructure.persistence.NotificationLogRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class NotificationController {

    private final NotificationLogRepository notificationLogRepository;
    private final FailedNotificationRepository failedNotificationRepository;

    public NotificationController(NotificationLogRepository notificationLogRepository,
                                   FailedNotificationRepository failedNotificationRepository) {
        this.notificationLogRepository = notificationLogRepository;
        this.failedNotificationRepository = failedNotificationRepository;
    }

    @GetMapping("/api/notifications")
    public List<NotificationLogEntry> sent() {
        return notificationLogRepository.findAllByOrderByNotifiedAtDesc();
    }

    @GetMapping("/api/notifications/dead-letter")
    public List<FailedNotificationEntry> deadLettered() {
        return failedNotificationRepository.findAllByOrderByMovedToDlqAtDesc();
    }
}
