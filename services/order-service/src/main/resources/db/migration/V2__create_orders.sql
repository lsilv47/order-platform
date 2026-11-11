CREATE TABLE orders (
  id UUID PRIMARY KEY,
  correlation_id VARCHAR(36) NOT NULL,
  customer_email VARCHAR(255) NOT NULL,
  status VARCHAR(30) NOT NULL,
  total_amount DECIMAL(10,2) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);
