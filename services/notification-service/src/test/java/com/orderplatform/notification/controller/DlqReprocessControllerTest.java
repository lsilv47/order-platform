package com.orderplatform.notification.controller;

import com.orderplatform.notification.AbstractIntegrationTest;
import com.orderplatform.notification.messaging.event.PaymentApprovedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DlqReprocessControllerTest extends AbstractIntegrationTest {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldReprocessDlqMessages() {
        var event = new PaymentApprovedEvent(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("25.00"),
                "pi_dlq_test", Instant.now()
        );

        // Send directly to DLQ
        rabbitTemplate.convertAndSend("notification.payment-result.dlq", event);

        // Trigger reprocess
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/admin/dlq/reprocess", null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"reprocessed\":1");
    }

    @Test
    void shouldReturnZeroWhenDlqIsEmpty() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/admin/dlq/reprocess", null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"reprocessed\":0");
    }
}
