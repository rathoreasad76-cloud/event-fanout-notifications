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
 * One row per event received on the audit-log queue. The unique constraint on
 * (message_id) is what makes writing this row idempotent — SQS is at-least-once,
 * so the same message can be delivered twice; a duplicate insert is caught and
 * treated as "already recorded" rather than a real error. See AuditLogConsumer.
 */
@Entity
@Table(name = "audit_log_entries", uniqueConstraints = @UniqueConstraint(columnNames = "message_id"))
public class AuditLogEntry {

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

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected AuditLogEntry() {
    }

    public AuditLogEntry(String messageId, String eventType, String payload, Instant receivedAt) {
        this.messageId = messageId;
        this.eventType = eventType;
        this.payload = payload;
        this.receivedAt = receivedAt;
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

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
