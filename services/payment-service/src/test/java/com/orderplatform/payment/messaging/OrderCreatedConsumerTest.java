package com.orderplatform.payment.messaging;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.orderplatform.payment.AbstractIntegrationTest;
import com.orderplatform.payment.domain.PaymentStatus;
import com.orderplatform.payment.messaging.event.OrderCreatedEvent;
import com.orderplatform.payment.repository.PaymentRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class OrderCreatedConsumerTest extends AbstractIntegrationTest {

    static WireMockServer wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private PaymentRepository paymentRepository;

    @BeforeAll
    static void startWireMock() {
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @DynamicPropertySource
    static void stripeProperties(DynamicPropertyRegistry registry) {
        registry.add("app.stripe.secret-key", () -> "sk_test_fake");
        registry.add("stripe.api.base", () -> wireMock.baseUrl());
    }

    @Test
    void shouldCreatePendingPaymentOnOrderCreated() {
        UUID orderId = UUID.randomUUID();

        wireMock.stubFor(post(urlPathEqualTo("/v1/payment_intents"))
                .willReturn(okJson("""
                        {
                          "id": "pi_test_123",
                          "object": "payment_intent",
                          "amount": 9990,
                          "currency": "brl",
                          "status": "requires_payment_method"
                        }
                        """)));

        var event = new OrderCreatedEvent(orderId, UUID.randomUUID(), new BigDecimal("99.90"), Instant.now());
        rabbitTemplate.convertAndSend("order.events", "order.created", event);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var payment = paymentRepository.findByOrderId(orderId);
            assertThat(payment).isPresent();
            assertThat(payment.get().getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(payment.get().getStripePaymentIntentId()).isEqualTo("pi_test_123");
        });
    }
}
