package com.asadrathore.eventfanout.infrastructure.aws;

import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;

import static com.asadrathore.eventfanout.infrastructure.aws.MessagingTopology.AUDIT_LOG_QUEUE;
import static com.asadrathore.eventfanout.infrastructure.aws.MessagingTopology.NOTIFICATION_DLQ;
import static com.asadrathore.eventfanout.infrastructure.aws.MessagingTopology.NOTIFICATION_QUEUE;
import static com.asadrathore.eventfanout.infrastructure.aws.MessagingTopology.TOPIC_NAME;

/**
 * Single place that knows how to turn topology names into ARNs/URLs, so
 * publisher and consumer beans don't each need a pre-resolved value injected at
 * construction time — which would otherwise create a startup-order dependency on
 * {@link TopologyBootstrapper} having already run before these beans are built.
 *
 * <p>SNS's create-topic-by-name is idempotent (returns the existing ARN if it's
 * already there), so {@link #topicArn()} is safe to call from the publisher on
 * every request without needing to know whether the topic already exists.
 * Queue URL lookups, by contrast, fail if the queue doesn't exist yet — callers
 * (the consumers) are expected to tolerate that during the brief startup window
 * before {@link TopologyBootstrapper} has created it, and simply skip that poll.
 * Resolved values are cached after the first successful lookup.
 */
@Component
public class TopologyResolver {

    private final SnsClient snsClient;
    private final SqsClient sqsClient;

    private volatile String topicArn;
    private volatile String auditLogQueueUrl;
    private volatile String notificationQueueUrl;
    private volatile String notificationDlqUrl;

    public TopologyResolver(SnsClient snsClient, SqsClient sqsClient) {
        this.snsClient = snsClient;
        this.sqsClient = sqsClient;
    }

    public String topicArn() {
        if (topicArn == null) {
            topicArn = snsClient.createTopic(b -> b.name(TOPIC_NAME)).topicArn();
        }
        return topicArn;
    }

    public String auditLogQueueUrl() {
        if (auditLogQueueUrl == null) {
            auditLogQueueUrl = lookupQueueUrl(AUDIT_LOG_QUEUE);
        }
        return auditLogQueueUrl;
    }

    public String notificationQueueUrl() {
        if (notificationQueueUrl == null) {
            notificationQueueUrl = lookupQueueUrl(NOTIFICATION_QUEUE);
        }
        return notificationQueueUrl;
    }

    public String notificationDlqUrl() {
        if (notificationDlqUrl == null) {
            notificationDlqUrl = lookupQueueUrl(NOTIFICATION_DLQ);
        }
        return notificationDlqUrl;
    }

    private String lookupQueueUrl(String queueName) {
        return sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build()).queueUrl();
    }
}
