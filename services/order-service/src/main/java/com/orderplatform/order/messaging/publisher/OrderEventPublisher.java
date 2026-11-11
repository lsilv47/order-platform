package com.orderplatform.order.messaging.publisher;

import com.orderplatform.order.domain.Order;
import com.orderplatform.order.messaging.event.OrderCreatedEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class OrderEventPublisher {

    private static final String ORDER_EVENTS_EXCHANGE = "order.events";
    private static final String ORDER_CREATED_ROUTING_KEY = "order.created";

    private final RabbitTemplate rabbitTemplate;

    public OrderEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishOrderCreated(Order order, String correlationId) {
        OrderCreatedEvent event = new OrderCreatedEvent(
                UUID.randomUUID(),
                correlationId,
                order.getId(),
                order.getCustomerEmail(),
                order.getTotalAmount(),
                order.getItems().stream()
                        .map(i -> new OrderCreatedEvent.Item(i.getProductId(), i.getQuantity(), i.getUnitPrice()))
                        .toList(),
                order.getCreatedAt()
        );
        rabbitTemplate.convertAndSend(ORDER_EVENTS_EXCHANGE, ORDER_CREATED_ROUTING_KEY, event, msg -> {
            msg.getMessageProperties().setCorrelationId(correlationId);
            return msg;
        });
    }
}
