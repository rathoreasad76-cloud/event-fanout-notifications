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
 * A successfully processed, customer-facing notification. Same idempotency
 * approach as AuditLogEntry — unique on message_id.
 */
@Entity
@Table(name = "notification_log_entries", uniqueConstraints = @UniqueConstraint(columnNames = "message_id"))
public class NotificationLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false)
    private String messageId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(nullable = false)
    private String payload;

    @Column(name = "notified_at", nullable = false)
    private Instant notifiedAt;

    protected NotificationLogEntry() {
    }

    public NotificationLogEntry(String messageId, String eventType, String payload, Instant notifiedAt) {
        this.messageId = messageId;
        this.eventType = eventType;
        this.payload = payload;
        this.notifiedAt = notifiedAt;
    }

    public Long getId() {
        return id;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getNotifiedAt() {
        return notifiedAt;
    }
}
