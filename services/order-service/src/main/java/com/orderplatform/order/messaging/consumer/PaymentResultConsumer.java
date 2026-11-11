package com.orderplatform.order.messaging.consumer;

import com.orderplatform.order.domain.OrderStatus;
import com.orderplatform.order.messaging.event.PaymentApprovedEvent;
import com.orderplatform.order.messaging.event.PaymentFailedEvent;
import com.orderplatform.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RabbitListener(queues = "order.payment-result")
public class PaymentResultConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentResultConsumer.class);

    private final OrderService orderService;

    public PaymentResultConsumer(OrderService orderService) {
        this.orderService = orderService;
    }

    @RabbitHandler
    public void handlePaymentApproved(PaymentApprovedEvent event, Message message) {
        String correlationId = message.getMessageProperties().getCorrelationId();
        try {
            MDC.put("correlationId", correlationId != null ? correlationId : "");
            log.info("Payment approved for order {}", event.orderId());
            orderService.transitionTo(event.orderId(), OrderStatus.PAID);
        } finally {
            MDC.remove("correlationId");
        }
    }

    @RabbitHandler
    public void handlePaymentFailed(PaymentFailedEvent event, Message message) {
        String correlationId = message.getMessageProperties().getCorrelationId();
        try {
            MDC.put("correlationId", correlationId != null ? correlationId : "");
            log.info("Payment failed for order {}: {}", event.orderId(), event.reason());
            orderService.transitionTo(event.orderId(), OrderStatus.PAYMENT_FAILED);
        } finally {
            MDC.remove("correlationId");
        }
    }
}
