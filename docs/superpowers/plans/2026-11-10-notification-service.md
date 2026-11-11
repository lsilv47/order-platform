# Notification Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Consume PaymentApproved and PaymentFailed events from RabbitMQ and send confirmation emails via JavaMail. Include DLQ reprocessing admin endpoint.

**Architecture:** Spring Boot 3.3.5 with no database. Pure event consumer + email sender. MailHog in dev/test captures emails without sending them for real.

**Tech Stack:** Java 21, Spring Boot 3.3.5, Spring AMQP, Spring Mail (JavaMail), Testcontainers (RabbitMQ + Mailhog via GenericContainer)

**Spec:** See technical refinement — notification-service consumers section, DLQ/reprocessing, email templates.

## Global Constraints

- Java 21; compile with `mvn test -pl services/notification-service` from monorepo root
- No database — no JPA, no Flyway
- Correlation ID comes from `MessageProperties.getCorrelationId()`; set in MDC for every consumer invocation
- If `JavaMailSender` throws → let exception propagate so RabbitMQ retries (do NOT catch and swallow)
- After `x-delivery-count` exceeds RabbitMQ queue limit, message goes to DLQ automatically
- Base package: `com.orderplatform.notification`
- Email from address: `${app.mail.from}`

## Review Focus

- **Mail exception propagation:** catching `MailException` and returning normally causes the message to be ACKed even though the email was not sent — always rethrow
- **Deserialization routing:** both `PaymentApproved` and `PaymentFailed` arrive on the same queue with different JSON shapes; use two separate `@RabbitListener` methods with explicit `MessageConverter` type mapping, not one generic `Object` handler
- **DLQ reprocessing idempotency:** reprocessed email may be sent twice if the original also delivered; this is acceptable for a portfolio — document it in the README failure scenarios section

---

### Task 1: RabbitMQ config + PaymentResult consumers + email sending

**Files:**
- Create: `src/main/java/com/orderplatform/notification/config/RabbitConfig.java`
- Create: `src/main/java/com/orderplatform/notification/config/CorrelationIdFilter.java`
- Create: `src/main/java/com/orderplatform/notification/messaging/event/PaymentApprovedEvent.java`
- Create: `src/main/java/com/orderplatform/notification/messaging/event/PaymentFailedEvent.java`
- Create: `src/main/java/com/orderplatform/notification/service/EmailNotificationService.java`
- Create: `src/main/java/com/orderplatform/notification/messaging/consumer/PaymentResultConsumer.java`
- Create: `src/test/java/com/orderplatform/notification/AbstractIntegrationTest.java`
- Test: `src/test/java/com/orderplatform/notification/messaging/PaymentResultConsumerTest.java`

**Interfaces:**
- Produces: `PaymentApprovedEvent` and `PaymentFailedEvent` records — same field shapes as payment-service (must deserialize the same JSON)
- Produces: `RabbitConfig` — declares queue `notification.payment-result` (binds to `payment.events`, routing key `payment.#`, DLX args), DLQ `notification.payment-result.dlq`, exchange `payment.events` (TopicExchange), exchange `payment.events.dlx`; `Jackson2JsonMessageConverter` as default
- Produces: `EmailNotificationService @Service` — `sendPaymentApprovedEmail(PaymentApprovedEvent event): void` sends email to `event.customerEmail()` with subject `"Pedido #" + event.orderId() + " confirmado!"` and body with order ID and amount; `sendPaymentFailedEmail(PaymentFailedEvent event): void` with subject `"Problema no pagamento do pedido #" + event.orderId()`
- Produces: `PaymentResultConsumer @Component` — `@RabbitListener(queues = "notification.payment-result")` with two methods dispatching by type (use `@RabbitHandler` pattern or inspect event type via JSON `__TypeId__` header); sets correlationId in MDC; calls email service

- [ ] **Step 1: Write failing consumer + email test**

```java
@SpringBootTest
class PaymentResultConsumerTest extends AbstractIntegrationTest {
    @Autowired RabbitTemplate template;
    // AbstractIntegrationTest starts RabbitMQ via Testcontainers
    // Also start MailHog as GenericContainer on port 1025 (SMTP) and 8025 (API)
    // Send a PaymentApprovedEvent to exchange "payment.events" routing key "payment.approved"
    // Poll MailHog HTTP API GET http://localhost:8025/api/v2/messages for up to 5 seconds
    // Assert at least one message received with subject containing orderId
}
```

- [ ] **Step 2: Implement `AbstractIntegrationTest`** — `@Testcontainers`. `RabbitMQContainer`. `GenericContainer` for MailHog: `new GenericContainer<>("mailhog/mailhog:latest").withExposedPorts(1025, 8025)`. Override `spring.rabbitmq.*`, `spring.mail.host`, `spring.mail.port` via `@DynamicPropertySource`.

- [ ] **Step 3: Implement event records** — `PaymentApprovedEvent` and `PaymentFailedEvent` as Java records.

- [ ] **Step 4: Implement `RabbitConfig`** — declare all required exchanges, queues, bindings. Register `Jackson2JsonMessageConverter`.

- [ ] **Step 5: Implement `EmailNotificationService`** — `@Service`. Inject `JavaMailSender` and `@Value("${app.mail.from}") String from`. Build `SimpleMailMessage` with to, from, subject, text; call `mailSender.send(message)`. Do NOT catch `MailException`.

- [ ] **Step 6: Implement `PaymentResultConsumer`** — `@Component`. Use `@RabbitListener(queues = "notification.payment-result")` with `@RabbitHandler` on each method, typed to `PaymentApprovedEvent` and `PaymentFailedEvent`. Set/remove MDC in each handler.

- [ ] **Step 7: Run test**

```bash
mvn test -pl services/notification-service -Dtest=PaymentResultConsumerTest
```
Expected: PASS — email arrives in MailHog.

- [ ] **Step 8: Commit**

```bash
git commit -am "feat(notification-service): add PaymentResult consumers and email sending"
```

---

### Task 2: DLQ reprocessing admin endpoint + full suite

**Files:**
- Create: `src/main/java/com/orderplatform/notification/controller/DlqReprocessController.java`
- Create: `src/main/java/com/orderplatform/notification/service/DlqReprocessService.java`
- Test: `src/test/java/com/orderplatform/notification/controller/DlqReprocessControllerTest.java`

**Interfaces:**
- Produces: `DlqReprocessService` — `reprocess(): int` reads up to 100 messages from `notification.payment-result.dlq` using `RabbitTemplate.receive(queueName, 1000)` in a loop; republishes each to `payment.events` with original routing key; returns count of reprocessed messages
- Produces: `POST /api/v1/admin/dlq/reprocess` → 200 `{ "reprocessed": N }` — no auth required for portfolio simplicity (document this in README as a known shortcut)

- [ ] **Step 1: Write failing test**

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
class DlqReprocessControllerTest extends AbstractIntegrationTest {
    @Autowired RabbitTemplate template;
    @Autowired TestRestTemplate rest;
    // Send a message directly to "notification.payment-result.dlq"
    // POST /api/v1/admin/dlq/reprocess
    // Assert response body reprocessed == 1
    // Assert message is now consumable from "notification.payment-result"
}
```

- [ ] **Step 2: Implement `DlqReprocessService`** — loop calling `rabbitTemplate.receive("notification.payment-result.dlq", 1000L)`; while message is not null: extract original routing key from `x-death` header or default to `payment.approved`; call `rabbitTemplate.send("payment.events", routingKey, message)`; increment counter; return counter.

- [ ] **Step 3: Implement `DlqReprocessController`** — `@RestController @RequestMapping("/api/v1/admin")`. `POST /dlq/reprocess` calls `dlqReprocessService.reprocess()`, returns `ResponseEntity.ok(Map.of("reprocessed", count))`.

- [ ] **Step 4: Run full test suite**

```bash
mvn test -pl services/notification-service
```
Expected: all PASS.

- [ ] **Step 5: Commit**

```bash
git commit -am "feat(notification-service): add DLQ reprocessing endpoint — notification-service complete"
```
