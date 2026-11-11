package com.orderplatform.order.messaging.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderCreatedEvent(
        UUID eventId,
        String correlationId,
        UUID orderId,
        String customerEmail,
        BigDecimal totalAmount,
        List<Item> items,
        Instant createdAt
) {
    public record Item(UUID productId, Integer quantity, BigDecimal unitPrice) {}
}
