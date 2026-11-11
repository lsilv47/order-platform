package com.orderplatform.payment.messaging.consumer;

import com.orderplatform.payment.domain.Payment;
import com.orderplatform.payment.domain.PaymentStatus;
import com.orderplatform.payment.messaging.event.OrderCreatedEvent;
import com.orderplatform.payment.repository.PaymentRepository;
import com.orderplatform.payment.service.StripeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class OrderCreatedConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedConsumer.class);

    private final StripeService stripeService;
    private final PaymentRepository paymentRepository;

    public OrderCreatedConsumer(StripeService stripeService, PaymentRepository paymentRepository) {
        this.stripeService = stripeService;
        this.paymentRepository = paymentRepository;
    }

    @RabbitListener(queues = "payment.order-created")
    public void handle(OrderCreatedEvent event, Message message) {
        String correlationId = message.getMessageProperties().getCorrelationId();
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put("correlationId", correlationId);

        try {
            log.info("Received OrderCreated for order {}", event.orderId());

            if (paymentRepository.findByOrderId(event.orderId()).isPresent()) {
                log.info("Payment already exists for order {}, skipping", event.orderId());
                return;
            }

            String paymentIntentId = stripeService.createPaymentIntent(
                    event.orderId(), event.totalAmount(), correlationId);

            Payment payment = new Payment(
                    UUID.randomUUID(),
                    event.orderId(),
                    paymentIntentId,
                    event.totalAmount(),
                    PaymentStatus.PENDING
            );
            paymentRepository.save(payment);

            log.info("Payment {} created for order {} with intent {}", payment.getId(), event.orderId(), paymentIntentId);
        } catch (Exception e) {
            log.error("Failed to process OrderCreated for order {}", event.orderId(), e);
            throw new RuntimeException("Failed to create payment", e);
        } finally {
            MDC.remove("correlationId");
        }
    }
}
