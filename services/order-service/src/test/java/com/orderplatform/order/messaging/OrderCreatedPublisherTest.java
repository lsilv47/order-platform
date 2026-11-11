package com.orderplatform.order.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderplatform.order.AbstractIntegrationTest;
import com.orderplatform.order.domain.Order;
import com.orderplatform.order.domain.OrderItem;
import com.orderplatform.order.domain.OrderStatus;
import com.orderplatform.order.messaging.event.OrderCreatedEvent;
import com.orderplatform.order.messaging.publisher.OrderEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
class OrderCreatedPublisherTest extends AbstractIntegrationTest {

    @Autowired RabbitTemplate rabbitTemplate;
    @Autowired OrderEventPublisher publisher;
    @Autowired ObjectMapper objectMapper;

    @Test
    void publishOrderCreated_sendsMessageToPaymentQueue() throws Exception {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setCorrelationId("test-corr-id");
        order.setCustomerEmail("test@example.com");
        order.setStatus(OrderStatus.PENDING);
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());
        order.setTotalAmount(new BigDecimal("29.99"));

        OrderItem item = new OrderItem();
        item.setId(UUID.randomUUID());
        item.setOrder(order);
        item.setProductId(UUID.randomUUID());
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("29.99"));
        item.setSubtotal(new BigDecimal("29.99"));
        order.setItems(List.of(item));

        publisher.publishOrderCreated(order, "test-corr-id");

        Message message = rabbitTemplate.receive("payment.order-created", 3000);

        assertThat(message).isNotNull();
        OrderCreatedEvent event = objectMapper.readValue(message.getBody(), OrderCreatedEvent.class);
        assertThat(event.orderId()).isEqualTo(order.getId());
        assertThat(event.correlationId()).isEqualTo("test-corr-id");
        assertThat(event.customerEmail()).isEqualTo("test@example.com");
    }
}
