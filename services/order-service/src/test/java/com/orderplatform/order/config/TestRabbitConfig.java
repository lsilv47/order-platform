package com.orderplatform.order.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class TestRabbitConfig {

    @Bean
    public Queue paymentOrderCreatedQueue() {
        return QueueBuilder.durable(RabbitConfig.PAYMENT_ORDER_CREATED_QUEUE).build();
    }

    @Bean
    public Binding paymentOrderCreatedBinding(Queue paymentOrderCreatedQueue, TopicExchange orderEventsExchange) {
        return BindingBuilder.bind(paymentOrderCreatedQueue).to(orderEventsExchange).with("order.created");
    }
}
