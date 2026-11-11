package com.orderplatform.notification.messaging.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentApprovedEvent(
        UUID paymentId,
        UUID orderId,
        BigDecimal amount,
        String stripePaymentIntentId,
        Instant approvedAt
) {}
