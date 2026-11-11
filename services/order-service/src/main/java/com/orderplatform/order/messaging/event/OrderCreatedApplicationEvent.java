package com.orderplatform.order.messaging.event;

import com.orderplatform.order.domain.Order;

public record OrderCreatedApplicationEvent(Order order, String correlationId) {}
