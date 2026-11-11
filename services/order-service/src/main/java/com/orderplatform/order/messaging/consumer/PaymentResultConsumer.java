package com.orderplatform.order.messaging.consumer;

import com.orderplatform.order.domain.OrderStatus;
import com.orderplatform.order.exception.InvalidOrderStateException;
import com.orderplatform.order.exception.ResourceNotFoundException;
import com.orderplatform.order.messaging.event.PaymentApprovedEvent;
import com.orderplatform.order.messaging.event.PaymentFailedEvent;
import com.orderplatform.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentResultConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentResultConsumer.class);

    private final OrderService orderService;

    public PaymentResultConsumer(OrderService orderService) {
        this.orderService = orderService;
    }

    @RabbitListener(queues = "#{T(com.orderplatform.order.config.RabbitConfig).ORDER_PAYMENT_APPROVED_QUEUE}")
    public void handlePaymentApproved(PaymentApprovedEvent event, Message message) {
        String correlationId = message.getMessageProperties().getCorrelationId();
        try {
            MDC.put("correlationId", correlationId != null ? correlationId : "");
            log.info("Payment approved for order {}", event.orderId());
            orderService.transitionTo(event.orderId(), OrderStatus.PAID);
        } catch (InvalidOrderStateException e) {
            log.warn("Ignoring duplicate/invalid transition for order {}: {}", event.orderId(), e.getMessage());
        } catch (ResourceNotFoundException e) {
            throw new AmqpRejectAndDontRequeueException("Order not found: " + event.orderId(), e);
        } finally {
            MDC.remove("correlationId");
        }
    }

    @RabbitListener(queues = "#{T(com.orderplatform.order.config.RabbitConfig).ORDER_PAYMENT_FAILED_QUEUE}")
    public void handlePaymentFailed(PaymentFailedEvent event, Message message) {
        String correlationId = message.getMessageProperties().getCorrelationId();
        try {
            MDC.put("correlationId", correlationId != null ? correlationId : "");
            log.info("Payment failed for order {}: {}", event.orderId(), event.reason());
            orderService.transitionTo(event.orderId(), OrderStatus.PAYMENT_FAILED);
        } catch (InvalidOrderStateException e) {
            log.warn("Ignoring duplicate/invalid transition for order {}: {}", event.orderId(), e.getMessage());
        } catch (ResourceNotFoundException e) {
            throw new AmqpRejectAndDontRequeueException("Order not found: " + event.orderId(), e);
        } finally {
            MDC.remove("correlationId");
        }
    }
}
