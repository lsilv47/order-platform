# Payment Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the payment-service: consume OrderCreated, create PaymentIntent on Stripe sandbox, receive webhooks with idempotency, publish PaymentApproved/PaymentFailed.

**Architecture:** Spring Boot 3.3.5 REST service with PostgreSQL, Stripe Java SDK, Spring Retry for Stripe calls, Spring AMQP consumer+publisher, WireMock for Stripe in tests, Testcontainers for PostgreSQL and RabbitMQ.

**Tech Stack:** Java 21, Spring Boot 3.3.5, Stripe Java SDK 25.3.0, Spring Retry, Spring AMQP, Flyway, JJWT 0.12.3, Testcontainers, WireMock 3.4.2

**Spec:** See technical refinement — payment-service model (`payments`, `processed_webhook_events`), Stripe webhook idempotency logic, retry config.

## Global Constraints

- Java 21; compile with `mvn test -pl services/payment-service` from monorepo root
- Same ID/timestamp/BigDecimal conventions as order-service plan
- Idempotency rule: check `processed_webhook_events` BEFORE any processing; insert AFTER in same transaction
- Stripe amount: always in centavos (integer) = `totalAmount.multiply(100).longValue()`
- Currency: `"brl"`
- Correlation ID propagated from `MessageProperties.getCorrelationId()` on AMQP messages
- Webhook endpoint `POST /api/v1/webhooks/stripe` is PUBLIC (no JWT); all other endpoints require JWT
- Base package: `com.orderplatform.payment`

## Review Focus

- **Duplicate webhook:** `stripe_event_id` UNIQUE constraint must prevent double-processing even under concurrent requests — use `INSERT … ON CONFLICT DO NOTHING` or catch `DataIntegrityViolationException`
- **Stripe amount precision:** `BigDecimal` → `longValue()` truncates; always use `setScale(0, HALF_UP)` before converting
- **Retry on Stripe 5xx only:** `@Retryable` should include only `StripeException` where `statusCode >= 500`; card errors (4xx) must NOT be retried
- **Webhook raw body:** Stripe signature validation requires the raw request body bytes; do NOT let Spring parse it before the controller reads it — use `HttpServletRequest.getInputStream()` or `@RequestBody byte[]`
- **RabbitMQ message converter:** must be the same `Jackson2JsonMessageConverter` used in order-service so event POJOs deserialize correctly

---

### Task 1: Database layer — entities + migrations + Testcontainers base

**Files:**
- Create: `src/main/java/com/orderplatform/payment/domain/PaymentStatus.java`
- Create: `src/main/java/com/orderplatform/payment/domain/Payment.java`
- Create: `src/main/java/com/orderplatform/payment/domain/ProcessedWebhookEvent.java`
- Create: `src/main/resources/db/migration/V1__create_payments.sql`
- Create: `src/main/resources/db/migration/V2__create_processed_webhook_events.sql`
- Create: `src/main/resources/db/migration/V3__create_indexes.sql`
- Create: `src/test/java/com/orderplatform/payment/AbstractIntegrationTest.java`
- Test: `src/test/java/com/orderplatform/payment/domain/FlywayMigrationTest.java`

**Interfaces:**
- Produces: `PaymentStatus` enum: `PENDING, APPROVED, FAILED`
- Produces: `Payment` entity: `id UUID`, `orderId UUID`, `stripePaymentIntentId VARCHAR(255) UNIQUE`, `amount DECIMAL(10,2)`, `status PaymentStatus`, `failureReason TEXT nullable`, `createdAt TIMESTAMPTZ`, `updatedAt TIMESTAMPTZ`
- Produces: `ProcessedWebhookEvent` entity: `id UUID`, `stripeEventId VARCHAR(255) UNIQUE NOT NULL`, `eventType VARCHAR(100)`, `processedAt TIMESTAMPTZ`
- Produces: `AbstractIntegrationTest` with `PostgreSQLContainer` and `RabbitMQContainer` via `@DynamicPropertySource`

- [ ] **Step 1: Write migration SQL**

`V1__create_payments.sql`:
```sql
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
```

`V2__create_processed_webhook_events.sql`:
```sql
CREATE TABLE processed_webhook_events (
  id UUID PRIMARY KEY,
  stripe_event_id VARCHAR(255) NOT NULL UNIQUE,
  event_type VARCHAR(100) NOT NULL,
  processed_at TIMESTAMPTZ NOT NULL
);
```

`V3__create_indexes.sql`:
```sql
CREATE INDEX idx_payments_order_id ON payments(order_id);
CREATE INDEX idx_processed_events_stripe_id ON processed_webhook_events(stripe_event_id);
```

- [ ] **Step 2: Write failing migration test** — same pattern as order-service: `@SpringBootTest`, extend `AbstractIntegrationTest`, assert `payments` table exists.

- [ ] **Step 3: Implement `AbstractIntegrationTest`** — same pattern as order-service.

- [ ] **Step 4: Implement entity classes** — `PaymentStatus` enum, `Payment` entity, `ProcessedWebhookEvent` entity.

- [ ] **Step 5: Run test**

```bash
mvn test -pl services/payment-service -Dtest=FlywayMigrationTest
```

- [ ] **Step 6: Commit**

```bash
git commit -am "feat(payment-service): add domain entities and Flyway migrations"
```

---

### Task 2: Repositories + Security config + JWT validation

**Files:**
- Create: `src/main/java/com/orderplatform/payment/repository/PaymentRepository.java`
- Create: `src/main/java/com/orderplatform/payment/repository/ProcessedWebhookEventRepository.java`
- Create: `src/main/java/com/orderplatform/payment/config/JwtUtil.java`
- Create: `src/main/java/com/orderplatform/payment/config/JwtAuthenticationFilter.java`
- Create: `src/main/java/com/orderplatform/payment/config/SecurityConfig.java`
- Create: `src/main/java/com/orderplatform/payment/config/CorrelationIdFilter.java`

**Interfaces:**
- Produces: `PaymentRepository extends JpaRepository<Payment, UUID>` with `Optional<Payment> findByOrderId(UUID orderId)`
- Produces: `ProcessedWebhookEventRepository extends JpaRepository<ProcessedWebhookEvent, UUID>` with `boolean existsByStripeEventId(String stripeEventId)`
- Produces: `JwtUtil`, `JwtAuthenticationFilter`, `SecurityConfig` — identical logic to order-service; `SecurityConfig` permits `POST /api/v1/webhooks/stripe` publicly; all other endpoints require auth
- Produces: `CorrelationIdFilter` — identical to order-service

- [ ] **Step 1: Implement repositories** — `PaymentRepository` and `ProcessedWebhookEventRepository` as JPA interfaces.

- [ ] **Step 2: Implement security components** — copy logic from order-service plan Task 3; `SecurityConfig` permits `POST /api/v1/webhooks/**` without auth.

- [ ] **Step 3: Commit**

```bash
git commit -am "feat(payment-service): add repositories, JWT security, correlation ID filter"
```

---

### Task 3: RabbitMQ config + OrderCreated consumer + Stripe PaymentIntent creation

**Files:**
- Create: `src/main/java/com/orderplatform/payment/config/RabbitConfig.java`
- Create: `src/main/java/com/orderplatform/payment/messaging/event/OrderCreatedEvent.java`
- Create: `src/main/java/com/orderplatform/payment/messaging/event/PaymentApprovedEvent.java`
- Create: `src/main/java/com/orderplatform/payment/messaging/event/PaymentFailedEvent.java`
- Create: `src/main/java/com/orderplatform/payment/service/StripeService.java`
- Create: `src/main/java/com/orderplatform/payment/messaging/consumer/OrderCreatedConsumer.java`
- Test: `src/test/java/com/orderplatform/payment/messaging/OrderCreatedConsumerTest.java`

**Interfaces:**
- Consumes: `PaymentRepository`, `ProcessedWebhookEventRepository` from Task 2
- Produces: `OrderCreatedEvent` record — same fields as order-service's `OrderCreatedEvent` (must deserialize from same JSON)
- Produces: `RabbitConfig` — declares queue `payment.order-created` (binds to `order.events`, routing key `order.created`), queue `payment.order-created.dlq`, exchange `order.events.dlx`; also declares `payment.events` TopicExchange and `payment.events.dlx`; `Jackson2JsonMessageConverter` as default
- Produces: `StripeService` — `@Service`; `createPaymentIntent(UUID orderId, BigDecimal amount, String correlationId): String` returns `stripePaymentIntentId`; annotated with `@Retryable(retryFor = StripeException.class, maxAttemptsExpression = "${app.stripe.retry.max-attempts}")` with `@Backoff`; initializes `Stripe.apiKey` from `@Value("${app.stripe.secret-key}")` in `@PostConstruct`
- Produces: `OrderCreatedConsumer @RabbitListener(queues = "payment.order-created")` — receives `OrderCreatedEvent`, calls `stripeService.createPaymentIntent`, saves `Payment(status=PENDING)`, logs with correlationId

- [ ] **Step 1: Write failing consumer test with WireMock**

```java
@SpringBootTest
class OrderCreatedConsumerTest extends AbstractIntegrationTest {
    // Start WireMock on a fixed port, stub POST /v1/payment_intents → 200 with fake PaymentIntent JSON
    // Send OrderCreatedEvent to queue "payment.order-created"
    // Await (5 seconds) until paymentRepository.findByOrderId(orderId) is present
    // assert payment.status == PENDING
    // assert payment.stripePaymentIntentId == "pi_test_123" (from WireMock stub)
}
```

- [ ] **Step 2: Implement event records** — `OrderCreatedEvent`, `PaymentApprovedEvent`, `PaymentFailedEvent` as Java records matching the contracts in the technical refinement.

- [ ] **Step 3: Implement `RabbitConfig`** — declare all exchanges, queues, bindings. Configure `Jackson2JsonMessageConverter`.

- [ ] **Step 4: Implement `StripeService`** — in `createPaymentIntent`: build `PaymentIntentCreateParams` with amount in centavos (`totalAmount.multiply(new BigDecimal("100")).setScale(0, HALF_UP).longValue()`), currency `"brl"`, metadata `orderId` and `correlationId`; call `PaymentIntent.create(params)`; return `paymentIntent.getId()`.

- [ ] **Step 5: Implement `OrderCreatedConsumer`** — `@Component`. `@RabbitListener`. Extract correlationId from `MessageProperties`. Call `stripeService.createPaymentIntent`. Save `Payment`. Log at INFO level.

- [ ] **Step 6: Run test**

```bash
mvn test -pl services/payment-service -Dtest=OrderCreatedConsumerTest
```

- [ ] **Step 7: Commit**

```bash
git commit -am "feat(payment-service): add OrderCreated consumer and Stripe PaymentIntent creation"
```

---

### Task 4: Stripe webhook endpoint + idempotency + event publisher

**Files:**
- Create: `src/main/java/com/orderplatform/payment/controller/WebhookController.java`
- Create: `src/main/java/com/orderplatform/payment/service/WebhookService.java`
- Create: `src/main/java/com/orderplatform/payment/messaging/publisher/PaymentEventPublisher.java`
- Create: `src/main/java/com/orderplatform/payment/exception/GlobalExceptionHandler.java`
- Test: `src/test/java/com/orderplatform/payment/controller/WebhookControllerTest.java`

**Interfaces:**
- Consumes: `PaymentRepository`, `ProcessedWebhookEventRepository` from Task 2; `RabbitConfig` from Task 3
- Produces: `PaymentEventPublisher` — `publishPaymentApproved(Payment payment, String correlationId)` sends to exchange `payment.events` with routing key `payment.approved`; `publishPaymentFailed(Payment payment, String reason, String correlationId)` with routing key `payment.failed`
- Produces: `WebhookService.processWebhook(byte[] payload, String sigHeader): void` — validates Stripe signature with `Webhook.constructEvent(payload, sigHeader, webhookSecret)`; checks `processedWebhookEventRepository.existsByStripeEventId(event.getId())`; if already processed returns immediately; otherwise processes in `@Transactional`: update payment status, insert `ProcessedWebhookEvent`, publish event
- Produces: `WebhookController POST /api/v1/webhooks/stripe` — reads raw body as `byte[]` with `@RequestBody byte[] payload`; reads `Stripe-Signature` header; calls `webhookService.processWebhook`; always returns 200

- [ ] **Step 1: Write failing webhook test**

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
class WebhookControllerTest extends AbstractIntegrationTest {
    // Test 1: valid signature + payment_intent.succeeded → payment updated to APPROVED, event published
    // Test 2: same event sent twice → processed only once (check processedWebhookEvent count = 1)
    // Test 3: invalid signature → 400
    // Use Stripe test helper or WireMock to generate valid signed payload
}
```

- [ ] **Step 2: Implement `PaymentEventPublisher`** — same pattern as order-service's `OrderEventPublisher`; sends to `payment.events` exchange.

- [ ] **Step 3: Implement `WebhookService`** — `@Service @Transactional`. Load `Stripe.webhookSecret` from properties. `processWebhook`: call `Webhook.constructEvent`, check idempotency, update `Payment`, save `ProcessedWebhookEvent`, call publisher.

- [ ] **Step 4: Implement `WebhookController`** — `@RestController`. `POST /api/v1/webhooks/stripe`. Read body as `byte[]`. Read `Stripe-Signature` header. Call `webhookService.processWebhook`. Return `ResponseEntity.ok().build()`. Catch `SignatureVerificationException` → return 400.

- [ ] **Step 5: Implement `GlobalExceptionHandler`** — same error format as order-service.

- [ ] **Step 6: Run tests**

```bash
mvn test -pl services/payment-service -Dtest=WebhookControllerTest
```
Expected: all PASS.

- [ ] **Step 7: Commit**

```bash
git commit -am "feat(payment-service): add webhook endpoint with idempotency and event publisher"
```

---

### Task 5: Payment query endpoint + full test suite

**Files:**
- Create: `src/main/java/com/orderplatform/payment/dto/response/PaymentResponse.java`
- Create: `src/main/java/com/orderplatform/payment/controller/PaymentController.java`
- Test: `src/test/java/com/orderplatform/payment/controller/PaymentControllerTest.java`

**Interfaces:**
- Consumes: `PaymentRepository.findByOrderId` from Task 2
- Produces: `PaymentResponse` record: `UUID id`, `UUID orderId`, `String status`, `BigDecimal amount`, `String stripePaymentIntentId`, `Instant createdAt`
- Produces: `GET /api/v1/payments/{orderId}` → 200 `PaymentResponse` or 404

- [ ] **Step 1: Write failing test**

```java
@Test
void getPaymentByOrderIdReturns200() {
    // insert payment in DB, GET /api/v1/payments/{orderId} with JWT
    // assert status 200, body.orderId matches
}
```

- [ ] **Step 2: Implement `PaymentController`** — `@RestController @RequestMapping("/api/v1/payments")`. `GET /{orderId}` calls `paymentRepository.findByOrderId`, maps to `PaymentResponse`, or throws `ResourceNotFoundException`.

- [ ] **Step 3: Run full test suite**

```bash
mvn test -pl services/payment-service
```
Expected: all PASS.

- [ ] **Step 4: Commit**

```bash
git commit -am "feat(payment-service): add Payment query endpoint — payment-service complete"
```
