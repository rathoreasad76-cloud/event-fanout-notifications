package com.asadrathore.eventfanout.integration;

import com.asadrathore.eventfanout.api.PublishEventRequest;
import com.asadrathore.eventfanout.infrastructure.persistence.FailedNotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
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
 * Proves a message that always fails processing eventually lands on the
 * dead-letter queue — and gets drained into failed_notification_entries — rather
 * than looping forever or silently vanishing.
 *
 * <p>{@code app.notification-queue.max-receive-count} is overridden to 2 here
 * purely so the test doesn't have to wait through the default of 3 redeliveries
 * at the queue's visibility timeout; the mechanism being tested (SQS redrive
 * policy) doesn't care what the number is.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.notification-queue.max-receive-count=2"
)
@Testcontainers
class DeadLetterQueueIntegrationTest {

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
    private FailedNotificationRepository failedNotificationRepository;

    @Test
    void aPermanentlyFailingNotificationEventuallyLandsOnTheDeadLetterQueue() {
        PublishEventRequest poison = new PublishEventRequest("PaymentFailed", "customer-3", new BigDecimal("75.00"), "USD", true);
        restTemplate.postForEntity("/api/payment-events", new HttpEntity<>(poison), String.class);

        // Two failed receives (max-receive-count=2) plus the queue's visibility
        // timeout between each — generous timeout since LocalStack's default
        // visibility timeout is 30s and this needs two redelivery cycles.
        await().atMost(90, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(failedNotificationRepository.findAllByOrderByMovedToDlqAtDesc()).isNotEmpty());
    }
}
