package com.asadrathore.eventfanout.application;

import com.asadrathore.eventfanout.api.PublishEventRequest;
import com.asadrathore.eventfanout.infrastructure.aws.TopologyResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

import java.util.Map;

@Service
public class PaymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisher.class);

    private final SnsClient snsClient;
    private final ObjectMapper objectMapper;
    private final TopologyResolver topologyResolver;

    public PaymentEventPublisher(SnsClient snsClient, ObjectMapper objectMapper, TopologyResolver topologyResolver) {
        this.snsClient = snsClient;
        this.objectMapper = objectMapper;
        this.topologyResolver = topologyResolver;
    }

    public String publish(PublishEventRequest event) {
        try {
            String body = objectMapper.writeValueAsString(event);
            PublishResponse response = snsClient.publish(PublishRequest.builder()
                    .topicArn(topologyResolver.topicArn())
                    .message(body)
                    .messageAttributes(Map.of(
                            "eventType", MessageAttributeValue.builder()
                                    .dataType("String")
                                    .stringValue(event.eventType())
                                    .build()
                    ))
                    .build());
            log.info("Published {} for customer {} (SNS messageId={})", event.eventType(), event.customerId(), response.messageId());
            return response.messageId();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish payment event", e);
        }
    }
}
