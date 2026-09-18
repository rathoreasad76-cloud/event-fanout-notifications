package com.asadrathore.eventfanout.api;

import com.asadrathore.eventfanout.infrastructure.persistence.FailedNotificationEntry;
import com.asadrathore.eventfanout.infrastructure.persistence.FailedNotificationRepository;
import com.asadrathore.eventfanout.infrastructure.persistence.NotificationLogEntry;
import com.asadrathore.eventfanout.infrastructure.persistence.NotificationLogRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "Notifications", description = "What the filtered notification consumer processed or dead-lettered")
public class NotificationController {

    private final NotificationLogRepository notificationLogRepository;
    private final FailedNotificationRepository failedNotificationRepository;

    public NotificationController(NotificationLogRepository notificationLogRepository,
                                   FailedNotificationRepository failedNotificationRepository) {
        this.notificationLogRepository = notificationLogRepository;
        this.failedNotificationRepository = failedNotificationRepository;
    }

    @GetMapping("/api/notifications")
    @Operation(summary = "List sent notifications",
            description = "Events the notification consumer processed successfully, newest first.")
    public List<NotificationLogEntry> sent() {
        return notificationLogRepository.findAllByOrderByNotifiedAtDesc();
    }

    @GetMapping("/api/notifications/dead-letter")
    @Operation(summary = "List dead-lettered notifications",
            description = "Messages that exhausted their retries and were drained from the DLQ, newest first.")
    public List<FailedNotificationEntry> deadLettered() {
        return failedNotificationRepository.findAllByOrderByMovedToDlqAtDesc();
    }
}
