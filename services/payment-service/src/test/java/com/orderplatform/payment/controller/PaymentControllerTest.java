package com.orderplatform.payment.controller;

import com.orderplatform.payment.AbstractIntegrationTest;
import com.orderplatform.payment.domain.Payment;
import com.orderplatform.payment.domain.PaymentStatus;
import com.orderplatform.payment.repository.PaymentRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentControllerTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PaymentRepository paymentRepository;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    private Payment testPayment;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        testPayment = new Payment(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "pi_query_test",
                new BigDecimal("75.00"),
                PaymentStatus.APPROVED
        );
        paymentRepository.save(testPayment);
    }

    @Test
    void getPaymentByOrderIdReturns200() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + generateToken());

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/payments/" + testPayment.getOrderId(),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains(testPayment.getOrderId().toString());
    }

    @Test
    void getPaymentByOrderIdReturns404WhenNotFound() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + generateToken());

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/payments/" + UUID.randomUUID(),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getPaymentWithoutAuthReturns401() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/payments/" + testPayment.getOrderId(),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private String generateToken() {
        return Jwts.builder()
                .subject("test-user")
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
