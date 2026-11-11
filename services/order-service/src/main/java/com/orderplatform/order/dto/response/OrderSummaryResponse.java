package com.orderplatform.order.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderSummaryResponse(
        UUID id,
        String status,
        BigDecimal totalAmount,
        String customerEmail,
        Instant createdAt
) {}
