package com.asadrathore.eventfanout.infrastructure.consumers;

import com.asadrathore.eventfanout.infrastructure.aws.TopologyResolver;
import com.asadrathore.eventfanout.infrastructure.persistence.AuditLogEntry;
import com.asadrathore.eventfanout.infrastructure.persistence.AuditLogRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Records every event that reaches the audit-log queue — this subscription has no
 * SNS filter policy, so it's the unfiltered firehose, unlike the notification
 * queue. Never fails deliberately; there's no DLQ path here because an audit
 * trail that can silently drop entries under load defeats its own purpose more
 * than a slow consumer does.
 */
@Component
public class AuditLogConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditLogConsumer.class);

    private final SqsClient sqsClient;
    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final TopologyResolver topologyResolver;

    public AuditLogConsumer(SqsClient sqsClient, AuditLogRepository repository, ObjectMapper objectMapper,
                             Clock clock, TopologyResolver topologyResolver) {
        this.sqsClient = sqsClient;
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.topologyResolver = topologyResolver;
    }

    @Scheduled(fixedDelay = 2000)
    public void poll() {
        String queueUrl;
        try {
            queueUrl = topologyResolver.auditLogQueueUrl();
        } catch (QueueDoesNotExistException e) {
            log.debug("audit-log-queue not created yet, skipping this poll");
            return;
        }

        List<Message> messages = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .maxNumberOfMessages(10)
                        .waitTimeSeconds(1)
                        .messageAttributeNames("All")
                        .build())
                .messages();

        for (Message message : messages) {
            handle(queueUrl, message);
        }
    }

    private void handle(String queueUrl, Message message) {
        if (!repository.existsByMessageId(message.messageId())) {
            String eventType = readEventType(message.body());
            repository.save(new AuditLogEntry(message.messageId(), eventType, message.body(), Instant.now(clock)));
            log.info("Audit-logged event type={} messageId={}", eventType, message.messageId());
        } else {
            log.debug("Duplicate delivery of messageId={} — already audit-logged, skipping insert", message.messageId());
        }

        // Delete regardless of whether this was a fresh insert or a duplicate —
        // either way, this consumer is done with the message.
        sqsClient.deleteMessage(DeleteMessageRequest.builder().queueUrl(queueUrl).receiptHandle(message.receiptHandle()).build());
    }

    private String readEventType(String body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            return node.path("eventType").asText("UNKNOWN");
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }
}
