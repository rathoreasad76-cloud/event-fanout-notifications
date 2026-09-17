package com.asadrathore.eventfanout.infrastructure.consumers;

import com.asadrathore.eventfanout.infrastructure.aws.TopologyResolver;
import com.asadrathore.eventfanout.infrastructure.persistence.AuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogConsumerTest {

    @Mock
    private SqsClient sqsClient;
    @Mock
    private AuditLogRepository repository;
    @Mock
    private TopologyResolver topologyResolver;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-16T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void aRedeliveredMessageIsNotInsertedTwiceButIsStillDeletedFromTheQueue() {
        when(topologyResolver.auditLogQueueUrl()).thenReturn("http://localstack/audit-log-queue");

        Message redelivered = Message.builder()
                .messageId("msg-already-seen")
                .body("{\"eventType\":\"PaymentProcessed\"}")
                .receiptHandle("receipt-1")
                .build();

        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(List.of(redelivered)).build());
        when(repository.existsByMessageId("msg-already-seen")).thenReturn(true);

        AuditLogConsumer consumer = new AuditLogConsumer(sqsClient, repository, objectMapper, clock, topologyResolver);
        consumer.poll();

        verify(repository, never()).save(any());
        verify(sqsClient, times(1)).deleteMessage(any(DeleteMessageRequest.class));
    }
}
