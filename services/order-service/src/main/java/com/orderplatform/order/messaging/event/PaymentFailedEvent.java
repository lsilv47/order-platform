package com.orderplatform.order.messaging.event;

import java.time.Instant;
import java.util.UUID;

public record PaymentFailedEvent(
        UUID eventId,
        String correlationId,
        UUID orderId,
        UUID paymentId,
        String customerEmail,
        String reason,
        Instant failedAt
) {}
