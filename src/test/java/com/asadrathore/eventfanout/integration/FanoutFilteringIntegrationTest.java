package com.asadrathore.eventfanout.integration;

import com.asadrathore.eventfanout.api.PublishEventRequest;
import com.asadrathore.eventfanout.infrastructure.persistence.AuditLogRepository;
import com.asadrathore.eventfanout.infrastructure.persistence.NotificationLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.client.TestRestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Proves the SNS filter policy actually filters: a routine PaymentProcessed
 * event should reach the (unfiltered) audit log but NOT the notification queue,
 * while a PaymentFailed event should reach both. If the filter policy in
 * TopologyBootstrapper is wrong or missing, this test fails — it's the thing
 * that would have caught a typo in the filter policy JSON.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class FanoutFilteringIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("event_fanout")
            .withUsername("event_fanout")
            .withPassword("event_fanout");

    @Container
    static LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.7"))
            .withServices(LocalStackContainer.Service.SNS, LocalStackContainer.Service.SQS);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("aws.endpoint-override", () -> localstack.getEndpoint().toString());
        registry.add("aws.region", localstack::getRegion);
        registry.add("aws.access-key", localstack::getAccessKey);
        registry.add("aws.secret-key", localstack::getSecretKey);
    }

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private NotificationLogRepository notificationLogRepository;

    @Test
    void routineEventReachesAuditLogButNotNotifications() {
        PublishEventRequest routine = new PublishEventRequest("PaymentProcessed", "customer-1", new BigDecimal("49.99"), "USD", false);
        ResponseEntity<String> response = restTemplate.postForEntity("/api/payment-events", new HttpEntity<>(routine), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(auditLogRepository.findAllByOrderByReceivedAtDesc())
                        .anyMatch(e -> e.getEventType().equals("PaymentProcessed")));

        // Give the notification consumer a fair chance to (wrongly) pick it up
        // before asserting it never does.
        await().during(3, TimeUnit.SECONDS).atMost(6, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(notificationLogRepository.findAllByOrderByNotifiedAtDesc())
                        .noneMatch(e -> e.getEventType().equals("PaymentProcessed")));
    }

    @Test
    void customerFacingEventReachesBothAuditLogAndNotifications() {
        PublishEventRequest failure = new PublishEventRequest("PaymentFailed", "customer-2", new BigDecimal("120.00"), "USD", false);
        restTemplate.postForEntity("/api/payment-events", new HttpEntity<>(failure), String.class);

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(auditLogRepository.findAllByOrderByReceivedAtDesc())
                        .anyMatch(e -> e.getEventType().equals("PaymentFailed")));

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(notificationLogRepository.findAllByOrderByNotifiedAtDesc())
                        .anyMatch(e -> e.getEventType().equals("PaymentFailed")));
    }
}
