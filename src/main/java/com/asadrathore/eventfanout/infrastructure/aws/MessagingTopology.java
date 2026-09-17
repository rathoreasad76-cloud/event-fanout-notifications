package com.asadrathore.eventfanout.infrastructure.aws;

/**
 * Names for the topic/queues this project wires up. Centralised here rather than
 * scattered as string literals across publisher/consumer/bootstrapper classes.
 */
public final class MessagingTopology {

    public static final String TOPIC_NAME = "payment-events";
    public static final String AUDIT_LOG_QUEUE = "audit-log-queue";
    public static final String NOTIFICATION_QUEUE = "notification-queue";
    public static final String NOTIFICATION_DLQ = "notification-dlq";

    /**
     * Event types that customers should actually be notified about. Everything
     * else still reaches the audit log (unfiltered) but doesn't trigger a
     * notification — this is what the SNS filter policy on the notification
     * queue's subscription encodes.
     */
    public static final String[] NOTIFIABLE_EVENT_TYPES = {"PaymentFailed", "RefundIssued"};

    private MessagingTopology() {
    }
}
