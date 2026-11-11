package com.orderplatform.payment.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    // --- Order events (consume) ---

    @Bean
    public TopicExchange orderEventsExchange() {
        return new TopicExchange("order.events");
    }

    @Bean
    public FanoutExchange orderEventsDlx() {
        return new FanoutExchange("order.events.dlx");
    }

    @Bean
    public Queue orderCreatedDlq() {
        return QueueBuilder.durable("payment.order-created.dlq").build();
    }

    @Bean
    public Binding orderCreatedDlqBinding() {
        return BindingBuilder.bind(orderCreatedDlq()).to(orderEventsDlx());
    }

    @Bean
    public Queue orderCreatedQueue() {
        return QueueBuilder.durable("payment.order-created")
                .withArgument("x-dead-letter-exchange", "order.events.dlx")
                .build();
    }

    @Bean
    public Binding orderCreatedBinding() {
        return BindingBuilder.bind(orderCreatedQueue())
                .to(orderEventsExchange())
                .with("order.created");
    }

    // --- Payment events (publish) ---

    @Bean
    public TopicExchange paymentEventsExchange() {
        return new TopicExchange("payment.events");
    }

    @Bean
    public FanoutExchange paymentEventsDlx() {
        return new FanoutExchange("payment.events.dlx");
    }

    // --- Message converter ---

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}
