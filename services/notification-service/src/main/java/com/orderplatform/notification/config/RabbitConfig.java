package com.orderplatform.notification.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    public TopicExchange paymentEventsExchange() {
        return new TopicExchange("payment.events");
    }

    @Bean
    public FanoutExchange paymentEventsDlx() {
        return new FanoutExchange("payment.events.dlx");
    }

    @Bean
    public Queue paymentResultDlq() {
        return QueueBuilder.durable("notification.payment-result.dlq").build();
    }

    @Bean
    public Binding paymentResultDlqBinding() {
        return BindingBuilder.bind(paymentResultDlq()).to(paymentEventsDlx());
    }

    @Bean
    public Queue paymentResultQueue() {
        return QueueBuilder.durable("notification.payment-result")
                .withArgument("x-dead-letter-exchange", "payment.events.dlx")
                .build();
    }

    @Bean
    public Binding paymentResultBinding() {
        return BindingBuilder.bind(paymentResultQueue())
                .to(paymentEventsExchange())
                .with("payment.#");
    }

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
