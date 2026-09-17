package com.asadrathore.eventfanout.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * A message that exhausted its receive count on the notification queue and
 * landed on the dead-letter queue. Drained here by DeadLetterDrainer so it's
 * queryable rather than just sitting invisibly in a DLQ until someone happens to
 * go looking — the point of a DLQ is that a human (or an alert) eventually
 * reviews it, not that messages disappear.
 */
@Entity
@Table(name = "failed_notification_entries", uniqueConstraints = @UniqueConstraint(columnNames = "message_id"))
public class FailedNotificationEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false)
    private String messageId;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(nullable = false)
    private String payload;

    @Column(name = "moved_to_dlq_at", nullable = false)
    private Instant movedToDlqAt;

    protected FailedNotificationEntry() {
    }

    public FailedNotificationEntry(String messageId, String payload, Instant movedToDlqAt) {
        this.messageId = messageId;
        this.payload = payload;
        this.movedToDlqAt = movedToDlqAt;
    }

    public Long getId() {
        return id;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getMovedToDlqAt() {
        return movedToDlqAt;
    }
}
