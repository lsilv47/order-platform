package com.orderplatform.order.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
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
    public static final String ORDER_PAYMENT_APPROVED_QUEUE = "order.payment-approved";
    public static final String ORDER_PAYMENT_FAILED_QUEUE = "order.payment-failed";
    public static final String ORDER_PAYMENT_RESULT_DLQ = "order.payment-result.dlq";
    public static final String PAYMENT_EVENTS_DLX = "payment.events.dlx";
    // Test-only: owned by payment-service in prod; declared in TestRabbitConfig for integration tests
    public static final String PAYMENT_ORDER_CREATED_QUEUE = "payment.order-created";

    private final ObjectMapper objectMapper;

    public RabbitConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    public TopicExchange orderEventsExchange() {
        return new TopicExchange(ORDER_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange paymentEventsExchange() {
        return new TopicExchange(PAYMENT_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange paymentEventsDlx() {
        return new DirectExchange(PAYMENT_EVENTS_DLX, true, false);
    }

    @Bean
    public Queue orderPaymentResultDlq() {
        return QueueBuilder.durable(ORDER_PAYMENT_RESULT_DLQ).build();
    }

    @Bean
    public Binding orderPaymentResultDlqBinding(Queue orderPaymentResultDlq, DirectExchange paymentEventsDlx) {
        return BindingBuilder.bind(orderPaymentResultDlq).to(paymentEventsDlx).with("order.payment-result.dlq");
    }

    @Bean
    public Queue orderPaymentApprovedQueue() {
        return QueueBuilder.durable(ORDER_PAYMENT_APPROVED_QUEUE)
                .withArguments(Map.of(
                        "x-dead-letter-exchange", PAYMENT_EVENTS_DLX,
                        "x-dead-letter-routing-key", ORDER_PAYMENT_RESULT_DLQ
                ))
                .build();
    }

    @Bean
    public Queue orderPaymentFailedQueue() {
        return QueueBuilder.durable(ORDER_PAYMENT_FAILED_QUEUE)
                .withArguments(Map.of(
                        "x-dead-letter-exchange", PAYMENT_EVENTS_DLX,
                        "x-dead-letter-routing-key", ORDER_PAYMENT_RESULT_DLQ
                ))
                .build();
    }

    @Bean
    public Binding orderPaymentApprovedBinding(Queue orderPaymentApprovedQueue, TopicExchange paymentEventsExchange) {
        return BindingBuilder.bind(orderPaymentApprovedQueue).to(paymentEventsExchange).with("payment.approved");
    }

    @Bean
    public Binding orderPaymentFailedBinding(Queue orderPaymentFailedQueue, TopicExchange paymentEventsExchange) {
        return BindingBuilder.bind(orderPaymentFailedQueue).to(paymentEventsExchange).with("payment.failed");
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
