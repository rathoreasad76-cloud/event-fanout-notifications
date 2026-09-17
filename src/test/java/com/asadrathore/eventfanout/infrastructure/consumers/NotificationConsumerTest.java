package com.asadrathore.eventfanout.infrastructure.consumers;

import com.asadrathore.eventfanout.infrastructure.aws.TopologyResolver;
import com.asadrathore.eventfanout.infrastructure.persistence.NotificationLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationConsumerTest {

    @Mock
    private SqsClient sqsClient;
    @Mock
    private NotificationLogRepository repository;
    @Mock
    private TopologyResolver topologyResolver;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-16T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void simulatedFailureLeavesTheMessageOnTheQueueInsteadOfDeletingIt() {
        when(topologyResolver.notificationQueueUrl()).thenReturn("http://localstack/notification-queue");

        Message poisonMessage = Message.builder()
                .messageId("msg-1")
                .body("{\"eventType\":\"PaymentFailed\",\"customerId\":\"cust-1\",\"simulateFailure\":true}")
                .build();

        when(sqsClient.receiveMessage(any(software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(List.of(poisonMessage)).build());

        NotificationConsumer consumer = new NotificationConsumer(sqsClient, repository, objectMapper, clock, topologyResolver);

        assertThatThrownBy(consumer::poll).isInstanceOf(IllegalStateException.class);

        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
        verify(repository, never()).save(any());
    }
}
