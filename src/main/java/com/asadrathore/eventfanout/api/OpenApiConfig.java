package com.asadrathore.eventfanout.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API metadata for the generated spec. springdoc builds the paths and schemas
 * from the controllers themselves; Swagger UI is served at
 * {@code /swagger-ui.html} and the raw spec at {@code /v3/api-docs}.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI eventFanoutOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Event Fan-out Notifications API")
                .version("0.1.0")
                .description("Publish payment events to SNS and inspect what each filtered SQS "
                        + "consumer recorded, including messages that ended up on the dead-letter queue."));
    }
}
