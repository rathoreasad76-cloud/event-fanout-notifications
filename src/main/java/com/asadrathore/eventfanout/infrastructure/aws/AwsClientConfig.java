package com.asadrathore.eventfanout.infrastructure.aws;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.SnsClientBuilder;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

import java.net.URI;

/**
 * Same idea as in order-entitlement-service: {@code aws.endpoint-override} points
 * these clients at LocalStack for local dev/tests. Leave it unset and the SDK
 * falls back to normal endpoint resolution and the standard credential chain, so
 * the same beans would work unmodified against real AWS.
 */
@Configuration
public class AwsClientConfig {

    @Bean
    public SnsClient snsClient(
            @Value("${aws.region:us-east-1}") String region,
            @Value("${aws.endpoint-override:}") String endpointOverride,
            @Value("${aws.access-key:test}") String accessKey,
            @Value("${aws.secret-key:test}") String secretKey) {
        SnsClientBuilder builder = SnsClient.builder().region(Region.of(region));
        if (!endpointOverride.isBlank()) {
            builder = builder
                    .endpointOverride(URI.create(endpointOverride))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKey, secretKey)));
        }
        return builder.build();
    }

    @Bean
    public SqsClient sqsClient(
            @Value("${aws.region:us-east-1}") String region,
            @Value("${aws.endpoint-override:}") String endpointOverride,
            @Value("${aws.access-key:test}") String accessKey,
            @Value("${aws.secret-key:test}") String secretKey) {
        SqsClientBuilder builder = SqsClient.builder().region(Region.of(region));
        if (!endpointOverride.isBlank()) {
            builder = builder
                    .endpointOverride(URI.create(endpointOverride))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKey, secretKey)));
        }
        return builder.build();
    }
}
