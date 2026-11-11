package com.orderplatform.notification.messaging.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentFailedEvent(
        UUID paymentId,
        UUID orderId,
        BigDecimal amount,
        String reason,
        Instant failedAt
) {}
