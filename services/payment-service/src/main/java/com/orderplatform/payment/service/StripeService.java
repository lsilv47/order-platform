package com.orderplatform.payment.service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
public class StripeService {

    private static final Logger log = LoggerFactory.getLogger(StripeService.class);

    @Value("${app.stripe.secret-key}")
    private String apiKey;

    @PostConstruct
    void init() {
        Stripe.apiKey = apiKey;
    }

    @Retryable(
            retryFor = StripeException.class,
            maxAttemptsExpression = "${app.stripe.retry.max-attempts}",
            backoff = @Backoff(
                    delayExpression = "${app.stripe.retry.initial-interval-ms}",
                    multiplierExpression = "${app.stripe.retry.multiplier}",
                    maxDelayExpression = "${app.stripe.retry.max-interval-ms}"
            )
    )
    public String createPaymentIntent(UUID orderId, BigDecimal amount, String correlationId) throws StripeException {
        long centavos = amount.multiply(new BigDecimal("100"))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(centavos)
                .setCurrency("brl")
                .putMetadata("orderId", orderId.toString())
                .putMetadata("correlationId", correlationId)
                .build();

        log.info("Creating PaymentIntent for order {} amount {} centavos", orderId, centavos);
        PaymentIntent intent = PaymentIntent.create(params);
        log.info("PaymentIntent created: {}", intent.getId());
        return intent.getId();
    }
}
