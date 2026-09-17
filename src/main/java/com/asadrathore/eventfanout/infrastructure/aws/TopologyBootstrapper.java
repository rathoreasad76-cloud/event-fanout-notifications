package com.asadrathore.eventfanout.infrastructure.aws;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.SetQueueAttributesRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.asadrathore.eventfanout.infrastructure.aws.MessagingTopology.AUDIT_LOG_QUEUE;
import static com.asadrathore.eventfanout.infrastructure.aws.MessagingTopology.NOTIFIABLE_EVENT_TYPES;
import static com.asadrathore.eventfanout.infrastructure.aws.MessagingTopology.NOTIFICATION_DLQ;
import static com.asadrathore.eventfanout.infrastructure.aws.MessagingTopology.NOTIFICATION_QUEUE;
import static com.asadrathore.eventfanout.infrastructure.aws.MessagingTopology.TOPIC_NAME;

/**
 * Creates the topic, queues, dead-letter wiring, and subscriptions on startup.
 *
 * <p>Everything here is idempotent by construction — SNS/SQS create-by-name calls
 * return the existing resource's ARN/URL if it already exists rather than
 * erroring, so this is safe to run every time the app starts, including
 * repeatedly inside tests. That's deliberate: it means both {@code docker compose
 * up} and the Testcontainers integration tests get a fully wired topology with no
 * separate setup script to keep in sync (unlike order-entitlement-service, where
 * a shell init script handles this — here the topology itself, including filter
 * policies and the DLQ redrive policy, is the thing being demonstrated, so it's
 * written as ordinary application code instead of hidden in a script).
 */
@Component
@Order(0)
public class TopologyBootstrapper implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TopologyBootstrapper.class);

    private final SnsClient snsClient;
    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final int maxReceiveCount;

    public TopologyBootstrapper(
            SnsClient snsClient,
            SqsClient sqsClient,
            ObjectMapper objectMapper,
            @Value("${app.notification-queue.max-receive-count:3}") int maxReceiveCount) {
        this.snsClient = snsClient;
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
        this.maxReceiveCount = maxReceiveCount;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String topicArn = snsClient.createTopic(b -> b.name(TOPIC_NAME)).topicArn();

        String dlqUrl = sqsClient.createQueue(CreateQueueRequest.builder().queueName(NOTIFICATION_DLQ).build()).queueUrl();
        String dlqArn = queueArn(dlqUrl);

        Map<String, String> redrivePolicy = Map.of(
                "deadLetterTargetArn", dlqArn,
                "maxReceiveCount", String.valueOf(maxReceiveCount));
        String notificationQueueUrl = sqsClient.createQueue(CreateQueueRequest.builder()
                        .queueName(NOTIFICATION_QUEUE)
                        .attributes(Map.of(QueueAttributeName.REDRIVE_POLICY, objectMapper.writeValueAsString(redrivePolicy)))
                        .build())
                .queueUrl();
        String notificationQueueArn = queueArn(notificationQueueUrl);

        String auditLogQueueUrl = sqsClient.createQueue(CreateQueueRequest.builder().queueName(AUDIT_LOG_QUEUE).build()).queueUrl();
        String auditLogQueueArn = queueArn(auditLogQueueUrl);

        allowTopicToSendTo(auditLogQueueUrl, auditLogQueueArn, topicArn);
        allowTopicToSendTo(notificationQueueUrl, notificationQueueArn, topicArn);

        // Audit log gets everything — no filter policy means "all messages".
        snsClient.subscribe(SubscribeRequest.builder()
                .topicArn(topicArn)
                .protocol("sqs")
                .endpoint(auditLogQueueArn)
                .attributes(Map.of("RawMessageDelivery", "true"))
                .build());

        // Notifications only care about customer-facing events — everything else
        // (e.g. routine PaymentProcessed events) is filtered out at the SNS level,
        // so the consumer never even sees messages it would just ignore.
        Map<String, Object> filterPolicy = Map.of("eventType", List.of(NOTIFIABLE_EVENT_TYPES));
        Map<String, String> subscriptionAttributes = new HashMap<>();
        subscriptionAttributes.put("RawMessageDelivery", "true");
        subscriptionAttributes.put("FilterPolicy", objectMapper.writeValueAsString(filterPolicy));

        snsClient.subscribe(SubscribeRequest.builder()
                .topicArn(topicArn)
                .protocol("sqs")
                .endpoint(notificationQueueArn)
                .attributes(subscriptionAttributes)
                .build());

        log.info("Messaging topology ready: topic={} auditLogQueue={} notificationQueue={} (maxReceiveCount={}) dlq={}",
                topicArn, AUDIT_LOG_QUEUE, NOTIFICATION_QUEUE, maxReceiveCount, NOTIFICATION_DLQ);
    }

    private String queueArn(String queueUrl) {
        return sqsClient.getQueueAttributes(GetQueueAttributesRequest.builder()
                        .queueUrl(queueUrl)
                        .attributeNames(QueueAttributeName.QUEUE_ARN)
                        .build())
                .attributes()
                .get(QueueAttributeName.QUEUE_ARN);
    }

    /**
     * SNS can't deliver to an SQS queue unless the queue's own access policy says
     * so. This grants sqs:SendMessage to the topic specifically (scoped by
     * SourceArn), rather than opening the queue up broadly — the kind of detail
     * that's easy to skip in a toy example and then be surprised nothing arrives.
     */
    private void allowTopicToSendTo(String queueUrl, String queueArn, String topicArn) throws Exception {
        Map<String, Object> statement = Map.of(
                "Effect", "Allow",
                "Principal", Map.of("Service", "sns.amazonaws.com"),
                "Action", "sqs:SendMessage",
                "Resource", queueArn,
                "Condition", Map.of("ArnEquals", Map.of("aws:SourceArn", topicArn)));
        Map<String, Object> policy = Map.of(
                "Version", "2012-10-17",
                "Statement", List.of(statement));

        sqsClient.setQueueAttributes(SetQueueAttributesRequest.builder()
                .queueUrl(queueUrl)
                .attributes(Map.of(QueueAttributeName.POLICY, objectMapper.writeValueAsString(policy)))
                .build());
    }
}
