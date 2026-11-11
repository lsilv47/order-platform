package com.orderplatform.notification.messaging.consumer;

import com.orderplatform.notification.messaging.event.PaymentApprovedEvent;
import com.orderplatform.notification.messaging.event.PaymentFailedEvent;
import com.orderplatform.notification.service.EmailNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RabbitListener(queues = "notification.payment-result")
public class PaymentResultConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentResultConsumer.class);

    private final EmailNotificationService emailService;

    public PaymentResultConsumer(EmailNotificationService emailService) {
        this.emailService = emailService;
    }

    @RabbitHandler
    public void handleApproved(PaymentApprovedEvent event, Message message) {
        setCorrelationId(message);
        try {
            log.info("Received PaymentApproved for order {}", event.orderId());
            emailService.sendPaymentApprovedEmail(event);
        } finally {
            MDC.remove("correlationId");
        }
    }

    @RabbitHandler
    public void handleFailed(PaymentFailedEvent event, Message message) {
        setCorrelationId(message);
        try {
            log.info("Received PaymentFailed for order {}", event.orderId());
            emailService.sendPaymentFailedEmail(event);
        } finally {
            MDC.remove("correlationId");
        }
    }

    private void setCorrelationId(Message message) {
        String correlationId = message.getMessageProperties().getCorrelationId();
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put("correlationId", correlationId);
    }
}
