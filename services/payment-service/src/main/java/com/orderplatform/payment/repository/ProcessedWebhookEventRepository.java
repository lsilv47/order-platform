package com.orderplatform.payment.repository;

import com.orderplatform.payment.domain.ProcessedWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedWebhookEventRepository extends JpaRepository<ProcessedWebhookEvent, UUID> {
    boolean existsByStripeEventId(String stripeEventId);
}
