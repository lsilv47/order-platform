package com.orderplatform.payment.messaging.publisher;

import com.orderplatform.payment.domain.Payment;
import com.orderplatform.payment.messaging.event.PaymentApprovedEvent;
import com.orderplatform.payment.messaging.event.PaymentFailedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class PaymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisher.class);
    private static final String EXCHANGE = "payment.events";

    private final RabbitTemplate rabbitTemplate;

    public PaymentEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishPaymentApproved(Payment payment, String correlationId) {
        var event = new PaymentApprovedEvent(
                payment.getId(),
                payment.getOrderId(),
                payment.getAmount(),
                payment.getStripePaymentIntentId(),
                Instant.now()
        );
        rabbitTemplate.convertAndSend(EXCHANGE, "payment.approved", event, msg -> {
            msg.getMessageProperties().setCorrelationId(correlationId);
            return msg;
        });
        log.info("Published PaymentApproved for order {}", payment.getOrderId());
    }

    public void publishPaymentFailed(Payment payment, String reason, String correlationId) {
        var event = new PaymentFailedEvent(
                payment.getId(),
                payment.getOrderId(),
                payment.getAmount(),
                reason,
                Instant.now()
        );
        rabbitTemplate.convertAndSend(EXCHANGE, "payment.failed", event, msg -> {
            msg.getMessageProperties().setCorrelationId(correlationId);
            return msg;
        });
        log.info("Published PaymentFailed for order {}", payment.getOrderId());
    }
}
