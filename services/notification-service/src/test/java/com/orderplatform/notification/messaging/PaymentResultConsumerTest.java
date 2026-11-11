package com.orderplatform.notification.messaging;

import com.orderplatform.notification.AbstractIntegrationTest;
import com.orderplatform.notification.messaging.event.PaymentApprovedEvent;
import com.orderplatform.notification.messaging.event.PaymentFailedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class PaymentResultConsumerTest extends AbstractIntegrationTest {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldSendEmailOnPaymentApproved() {
        UUID orderId = UUID.randomUUID();
        var event = new PaymentApprovedEvent(
                UUID.randomUUID(), orderId, new BigDecimal("99.90"),
                "pi_test_123", Instant.now()
        );

        rabbitTemplate.convertAndSend("payment.events", "payment.approved", event);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    mailhogApiUrl() + "/api/v2/messages", String.class);
            assertThat(response.getBody()).contains(orderId.toString());
        });
    }

    @Test
    void shouldSendEmailOnPaymentFailed() {
        UUID orderId = UUID.randomUUID();
        var event = new PaymentFailedEvent(
                UUID.randomUUID(), orderId, new BigDecimal("50.00"),
                "Card declined", Instant.now()
        );

        rabbitTemplate.convertAndSend("payment.events", "payment.failed", event);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    mailhogApiUrl() + "/api/v2/messages", String.class);
            assertThat(response.getBody()).contains(orderId.toString());
        });
    }
}
