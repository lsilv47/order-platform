package com.orderplatform.payment.controller;

import com.orderplatform.payment.AbstractIntegrationTest;
import com.orderplatform.payment.domain.Payment;
import com.orderplatform.payment.domain.PaymentStatus;
import com.orderplatform.payment.repository.PaymentRepository;
import com.orderplatform.payment.repository.ProcessedWebhookEventRepository;
import com.stripe.net.Webhook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookControllerTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ProcessedWebhookEventRepository processedWebhookEventRepository;

    @Value("${app.stripe.webhook-secret}")
    private String webhookSecret;

    private Payment testPayment;

    @BeforeEach
    void setUp() {
        processedWebhookEventRepository.deleteAll();
        paymentRepository.deleteAll();

        testPayment = new Payment(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "pi_test_webhook",
                new BigDecimal("50.00"),
                PaymentStatus.PENDING
        );
        paymentRepository.save(testPayment);
    }

    @Test
    void invalidSignatureReturns400() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Stripe-Signature", "invalid_signature");
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/webhooks/stripe",
                HttpMethod.POST,
                new HttpEntity<>("{}", headers),
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void duplicateEventIsProcessedOnlyOnce() throws Exception {
        String eventId = "evt_" + UUID.randomUUID();
        String payload = buildPaymentIntentSucceededPayload(eventId, testPayment);
        String signature = Webhook.Util.computeHmacSha256(webhookSecret, payload);
        long timestamp = System.currentTimeMillis() / 1000;
        String sigHeader = "t=" + timestamp + ",v1=" + signature;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Stripe-Signature", sigHeader);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Send twice
        restTemplate.exchange("/api/v1/webhooks/stripe", HttpMethod.POST,
                new HttpEntity<>(payload.getBytes(), headers), Void.class);
        restTemplate.exchange("/api/v1/webhooks/stripe", HttpMethod.POST,
                new HttpEntity<>(payload.getBytes(), headers), Void.class);

        assertThat(processedWebhookEventRepository.count()).isEqualTo(1);
    }

    private String buildPaymentIntentSucceededPayload(String eventId, Payment payment) {
        return """
                {
                  "id": "%s",
                  "object": "event",
                  "type": "payment_intent.succeeded",
                  "data": {
                    "object": {
                      "id": "%s",
                      "object": "payment_intent",
                      "amount": %d,
                      "currency": "brl",
                      "status": "succeeded",
                      "metadata": {
                        "orderId": "%s",
                        "correlationId": "%s"
                      }
                    }
                  }
                }
                """.formatted(
                eventId,
                payment.getStripePaymentIntentId(),
                payment.getAmount().multiply(new BigDecimal("100")).longValue(),
                payment.getOrderId(),
                UUID.randomUUID()
        );
    }
}
