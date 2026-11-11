CREATE TABLE payments (
  id UUID PRIMARY KEY,
  order_id UUID NOT NULL UNIQUE,
  stripe_payment_intent_id VARCHAR(255) NOT NULL UNIQUE,
  amount DECIMAL(10,2) NOT NULL,
  status VARCHAR(30) NOT NULL,
  failure_reason TEXT,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);
