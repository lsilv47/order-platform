package com.orderplatform.payment.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_webhook_events")
public class ProcessedWebhookEvent {

    @Id
    private UUID id;

    @Column(name = "stripe_event_id", nullable = false, unique = true)
    private String stripeEventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedWebhookEvent() {}

    public ProcessedWebhookEvent(UUID id, String stripeEventId, String eventType) {
        this.id = id;
        this.stripeEventId = stripeEventId;
        this.eventType = eventType;
        this.processedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getStripeEventId() { return stripeEventId; }
    public String getEventType() { return eventType; }
    public Instant getProcessedAt() { return processedAt; }
}
