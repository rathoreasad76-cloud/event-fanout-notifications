package com.asadrathore.eventfanout.infrastructure.consumers;

import com.asadrathore.eventfanout.infrastructure.aws.TopologyResolver;
import com.asadrathore.eventfanout.infrastructure.persistence.NotificationLogEntry;
import com.asadrathore.eventfanout.infrastructure.persistence.NotificationLogRepository;
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
 * Consumes the (already filtered, by SNS) notification queue and "sends" a
 * customer notification — logged here rather than actually emailing/texting
 * anyone, since the point is the messaging pattern, not a notification provider
 * integration.
 *
 * <p>The important behaviour is what happens on failure: if {@code
 * simulateFailure} is set on the event, this throws instead of processing, and
 * — critically — does NOT delete the message from the queue. SQS makes the
 * message visible again after its visibility timeout, so it gets redelivered.
 * After {@code app.notification-queue.max-receive-count} failed receives, SQS
 * itself (not this code) moves the message to the dead-letter queue. That's the
 * mechanism that makes a DLQ actually work: the consumer's job is just to not
 * delete what it couldn't process, not to implement the retry counting itself.
 */
@Component
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final SqsClient sqsClient;
    private final NotificationLogRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final TopologyResolver topologyResolver;

    public NotificationConsumer(SqsClient sqsClient, NotificationLogRepository repository, ObjectMapper objectMapper,
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
            queueUrl = topologyResolver.notificationQueueUrl();
        } catch (QueueDoesNotExistException e) {
            log.debug("notification-queue not created yet, skipping this poll");
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
        JsonNode node = parse(message.body());

        if (node.path("simulateFailure").asBoolean(false)) {
            log.warn("Simulated failure processing messageId={} — leaving on queue for redelivery/DLQ", message.messageId());
            throw new IllegalStateException("Simulated notification failure for messageId=" + message.messageId());
        }

        if (!repository.existsByMessageId(message.messageId())) {
            String eventType = node.path("eventType").asText("UNKNOWN");
            String customerId = node.path("customerId").asText("UNKNOWN");
            repository.save(new NotificationLogEntry(message.messageId(), eventType, message.body(), Instant.now(clock)));
            log.info("Notified customer={} of event type={} messageId={}", customerId, eventType, message.messageId());
        } else {
            log.debug("Duplicate delivery of messageId={} — notification already sent, skipping", message.messageId());
        }

        sqsClient.deleteMessage(DeleteMessageRequest.builder().queueUrl(queueUrl).receiptHandle(message.receiptHandle()).build());
    }

    private JsonNode parse(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("Malformed notification payload", e);
        }
    }
}
