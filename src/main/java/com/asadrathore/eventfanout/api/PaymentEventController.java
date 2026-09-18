package com.asadrathore.eventfanout.api;

import com.asadrathore.eventfanout.application.PaymentEventPublisher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Tag(name = "Payment events", description = "Publish events to the SNS topic")
public class PaymentEventController {

    private final PaymentEventPublisher publisher;

    public PaymentEventController(PaymentEventPublisher publisher) {
        this.publisher = publisher;
    }

    @PostMapping("/api/payment-events")
    @Operation(summary = "Publish a payment event",
            description = "Publishes to SNS, which fans out to every subscribed queue whose filter policy matches.")
    @ApiResponse(responseCode = "202", description = "Accepted by SNS; body contains the SNS message id")
    @ApiResponse(responseCode = "400", description = "Request failed validation")
    public ResponseEntity<Map<String, String>> publish(@Valid @RequestBody PublishEventRequest request) {
        String messageId = publisher.publish(request);
        return ResponseEntity.accepted().body(Map.of("snsMessageId", messageId));
    }
}
