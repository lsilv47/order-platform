package com.orderplatform.payment.service;

import com.orderplatform.payment.domain.Payment;
import com.orderplatform.payment.domain.PaymentStatus;
import com.orderplatform.payment.domain.ProcessedWebhookEvent;
import com.orderplatform.payment.messaging.publisher.PaymentEventPublisher;
import com.orderplatform.payment.repository.PaymentRepository;
import com.orderplatform.payment.repository.ProcessedWebhookEventRepository;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private final PaymentRepository paymentRepository;
    private final ProcessedWebhookEventRepository processedWebhookEventRepository;
    private final PaymentEventPublisher paymentEventPublisher;

    @Value("${app.stripe.webhook-secret}")
    private String webhookSecret;

    public WebhookService(PaymentRepository paymentRepository,
                          ProcessedWebhookEventRepository processedWebhookEventRepository,
                          PaymentEventPublisher paymentEventPublisher) {
        this.paymentRepository = paymentRepository;
        this.processedWebhookEventRepository = processedWebhookEventRepository;
        this.paymentEventPublisher = paymentEventPublisher;
    }

    @Transactional
    public void processWebhook(byte[] payload, String sigHeader) throws SignatureVerificationException {
        Event event = Webhook.constructEvent(new String(payload), sigHeader, webhookSecret);

        if (processedWebhookEventRepository.existsByStripeEventId(event.getId())) {
            log.info("Webhook event {} already processed, skipping", event.getId());
            return;
        }

        String eventType = event.getType();
        log.info("Processing webhook event {} type {}", event.getId(), eventType);

        // Insert before business logic; UNIQUE constraint on stripe_event_id
        // prevents concurrent duplicate processing (transaction rolls back on conflict)
        processedWebhookEventRepository.saveAndFlush(
                new ProcessedWebhookEvent(UUID.randomUUID(), event.getId(), eventType)
        );

        switch (eventType) {
            case "payment_intent.succeeded" -> handlePaymentIntentSucceeded(event);
            case "payment_intent.payment_failed" -> handlePaymentIntentFailed(event);
            default -> log.info("Unhandled event type: {}", eventType);
        }
    }

    private void handlePaymentIntentSucceeded(Event event) {
        PaymentIntent intent = (PaymentIntent) event.getDataObjectDeserializer()
                .getObject().orElseThrow();
        String correlationId = intent.getMetadata().getOrDefault("correlationId", UUID.randomUUID().toString());
        MDC.put("correlationId", correlationId);

        Payment payment = paymentRepository.findByOrderId(
                UUID.fromString(intent.getMetadata().get("orderId"))
        ).orElseThrow(() -> new IllegalStateException("Payment not found for intent " + intent.getId()));

        payment.setStatus(PaymentStatus.APPROVED);
        paymentRepository.save(payment);
        paymentEventPublisher.publishPaymentApproved(payment, correlationId);
    }

    private void handlePaymentIntentFailed(Event event) {
        PaymentIntent intent = (PaymentIntent) event.getDataObjectDeserializer()
                .getObject().orElseThrow();
        String correlationId = intent.getMetadata().getOrDefault("correlationId", UUID.randomUUID().toString());
        MDC.put("correlationId", correlationId);

        Payment payment = paymentRepository.findByOrderId(
                UUID.fromString(intent.getMetadata().get("orderId"))
        ).orElseThrow(() -> new IllegalStateException("Payment not found for intent " + intent.getId()));

        String reason = intent.getLastPaymentError() != null
                ? intent.getLastPaymentError().getMessage()
                : "Unknown failure";

        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason(reason);
        paymentRepository.save(payment);
        paymentEventPublisher.publishPaymentFailed(payment, reason, correlationId);
    }
}
