package com.orderplatform.order.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String status,
        BigDecimal totalAmount,
        String customerEmail,
        List<OrderItemResponse> items,
        Instant createdAt,
        Instant updatedAt
) {}
