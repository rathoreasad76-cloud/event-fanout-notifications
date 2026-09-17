package com.asadrathore.eventfanout.infrastructure.consumers;

import com.asadrathore.eventfanout.infrastructure.aws.TopologyResolver;
import com.asadrathore.eventfanout.infrastructure.persistence.FailedNotificationEntry;
import com.asadrathore.eventfanout.infrastructure.persistence.FailedNotificationRepository;
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
 * A DLQ that nobody reads is just a place messages go to be forgotten. This
 * drains {@code notification-dlq} into a queryable table (see
 * GET /api/notifications/dead-letter) so a dead-lettered message is something a
 * human can actually find and act on, rather than invisible until someone thinks
 * to check the queue depth in the AWS console.
 */
@Component
public class DeadLetterDrainer {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterDrainer.class);

    private final SqsClient sqsClient;
    private final FailedNotificationRepository repository;
    private final Clock clock;
    private final TopologyResolver topologyResolver;

    public DeadLetterDrainer(SqsClient sqsClient, FailedNotificationRepository repository, Clock clock,
                              TopologyResolver topologyResolver) {
        this.sqsClient = sqsClient;
        this.repository = repository;
        this.clock = clock;
        this.topologyResolver = topologyResolver;
    }

    @Scheduled(fixedDelay = 5000)
    public void poll() {
        String dlqUrl;
        try {
            dlqUrl = topologyResolver.notificationDlqUrl();
        } catch (QueueDoesNotExistException e) {
            log.debug("notification-dlq not created yet, skipping this poll");
            return;
        }

        List<Message> messages = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                        .queueUrl(dlqUrl)
                        .maxNumberOfMessages(10)
                        .waitTimeSeconds(1)
                        .build())
                .messages();

        for (Message message : messages) {
            if (!repository.existsByMessageId(message.messageId())) {
                repository.save(new FailedNotificationEntry(message.messageId(), message.body(), Instant.now(clock)));
                log.warn("Drained dead-lettered messageId={} into failed_notification_entries for review", message.messageId());
            }
            sqsClient.deleteMessage(DeleteMessageRequest.builder().queueUrl(dlqUrl).receiptHandle(message.receiptHandle()).build());
        }
    }
}
