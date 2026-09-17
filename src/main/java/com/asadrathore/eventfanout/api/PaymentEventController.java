package com.asadrathore.eventfanout.api;

import com.asadrathore.eventfanout.application.PaymentEventPublisher;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class PaymentEventController {

    private final PaymentEventPublisher publisher;

    public PaymentEventController(PaymentEventPublisher publisher) {
        this.publisher = publisher;
    }

    @PostMapping("/api/payment-events")
    public ResponseEntity<Map<String, String>> publish(@Valid @RequestBody PublishEventRequest request) {
        String messageId = publisher.publish(request);
        return ResponseEntity.accepted().body(Map.of("snsMessageId", messageId));
    }
}
