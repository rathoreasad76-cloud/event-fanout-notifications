package com.asadrathore.eventfanout.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * {@code simulateFailure} exists purely to make the DLQ path demonstrable on
 * demand — the notification consumer throws instead of processing when it's
 * true, so you can watch a message survive a few redeliveries and land on the
 * dead-letter queue without needing to engineer a real failure.
 */
public record PublishEventRequest(
        @Schema(description = "Event type; the notification queue's filter policy only matches PaymentFailed and RefundIssued",
                example = "PaymentFailed")
        @NotBlank String eventType,
        @Schema(example = "customer-1")
        @NotBlank String customerId,
        @Schema(example = "120.00")
        @NotNull BigDecimal amount,
        @Schema(example = "USD")
        @NotBlank String currency,
        @Schema(description = "Make the notification consumer fail so the message ends up on the DLQ",
                example = "false")
        boolean simulateFailure
) {
}
