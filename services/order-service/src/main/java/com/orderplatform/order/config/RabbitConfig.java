package com.orderplatform.order.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class RabbitConfig {

    // Outbound: order-service publishes here
    public static final String ORDER_EVENTS_EXCHANGE = "order.events";
    // Inbound: payment results arrive here from payment-service
    public static final String PAYMENT_EVENTS_EXCHANGE = "payment.events";
    public static final String ORDER_PAYMENT_RESULT_QUEUE = "order.payment-result";
    public static final String ORDER_PAYMENT_RESULT_DLQ = "order.payment-result.dlq";
    public static final String PAYMENT_EVENTS_DLX = "payment.events.dlx";
    // Test queue — owned by payment-service in prod, declared here for integration tests
    public static final String PAYMENT_ORDER_CREATED_QUEUE = "payment.order-created";

    @Bean
    public TopicExchange orderEventsExchange() {
        return new TopicExchange(ORDER_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange paymentEventsExchange() {
        return new TopicExchange(PAYMENT_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange paymentEventsDlx() {
        return new TopicExchange(PAYMENT_EVENTS_DLX, true, false);
    }

    @Bean
    public Queue orderPaymentResultDlq() {
        return QueueBuilder.durable(ORDER_PAYMENT_RESULT_DLQ).build();
    }

    @Bean
    public Queue orderPaymentResultQueue() {
        return QueueBuilder.durable(ORDER_PAYMENT_RESULT_QUEUE)
                .withArguments(Map.of(
                        "x-dead-letter-exchange", PAYMENT_EVENTS_DLX,
                        "x-dead-letter-routing-key", ORDER_PAYMENT_RESULT_DLQ
                ))
                .build();
    }

    @Bean
    public Binding orderPaymentResultBinding(Queue orderPaymentResultQueue, TopicExchange paymentEventsExchange) {
        return BindingBuilder.bind(orderPaymentResultQueue).to(paymentEventsExchange).with("payment.#");
    }

    // Test queue: payment-service would own this in prod; declared here so tests can receive from it
    @Bean
    public Queue paymentOrderCreatedQueue() {
        return QueueBuilder.durable(PAYMENT_ORDER_CREATED_QUEUE).build();
    }

    @Bean
    public Binding paymentOrderCreatedBinding(Queue paymentOrderCreatedQueue, TopicExchange orderEventsExchange) {
        return BindingBuilder.bind(paymentOrderCreatedQueue).to(orderEventsExchange).with("order.created");
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        return factory;
    }
}
