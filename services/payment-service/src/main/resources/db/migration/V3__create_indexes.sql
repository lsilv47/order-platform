CREATE INDEX idx_payments_order_id ON payments(order_id);
CREATE INDEX idx_processed_events_stripe_id ON processed_webhook_events(stripe_event_id);
