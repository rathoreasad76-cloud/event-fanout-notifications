package com.asadrathore.eventfanout.api;

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
        @NotBlank String eventType,
        @NotBlank String customerId,
        @NotNull BigDecimal amount,
        @NotBlank String currency,
        boolean simulateFailure
) {
}
