package com.orderplatform.order.messaging.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentApprovedEvent(
        UUID eventId,
        String correlationId,
        UUID orderId,
        UUID paymentId,
        String customerEmail,
        BigDecimal totalAmount,
        Instant approvedAt
) {}
