# Order Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the order-service: products CRUD, order creation with stock reservation, state machine, JWT auth, and RabbitMQ publisher.

**Architecture:** Spring Boot 3.3.5 REST service with PostgreSQL (Flyway migrations), Spring Security JWT, Spring AMQP publisher, and Testcontainers integration tests. Part of a Maven multi-module monorepo at `services/order-service/`.

**Tech Stack:** Java 21, Spring Boot 3.3.5, Spring Data JPA, Spring Security, Spring AMQP, Flyway, JJWT 0.12.3, Testcontainers, JUnit 5

**Spec:** `docs/superpowers/plans/2026-11-10-order-service.md` + technical refinement at `docs/` (see entities, API contracts, state machine, event contracts in the refinement document)

## Global Constraints

- Java 21; compile with `mvn test -pl services/order-service` from monorepo root
- All IDs: `UUID`, generated via `UUID.randomUUID()` in the application, never the DB
- All monetary values: `BigDecimal`; DB column `DECIMAL(10,2)`; never `double`
- All timestamps: `Instant`; DB column `TIMESTAMPTZ`; JSON serialized as ISO-8601 `2024-01-15T10:00:00Z`
- Error response format: `{ "timestamp", "status", "error", "message", "correlationId" }`
- Correlation ID header name: `X-Correlation-Id` (UUID, generate if absent)
- Base package: `com.orderplatform.order`
- All endpoints prefixed `/api/v1/`
- JWT secret from env `JWT_SECRET`; admin credentials from `app.admin.email` / `app.admin.password`

## Review Focus

- **Stock concurrency:** two concurrent orders for the same product may both pass the stock check before either decrements — use `@Transactional` with pessimistic lock or `UPDATE … WHERE stock_quantity >= quantity`
- **State machine invalid transitions:** `PAID → CANCELLED` must throw 409; validate in `OrderService`, not controller
- **N+1 on order items:** `GET /orders/{id}` must fetch items in one query — use `@EntityGraph` or `JOIN FETCH`
- **BigDecimal precision:** `subtotal` must be `quantity.multiply(unitPrice)`, never floating-point arithmetic
- **JWT expiry:** expired token must return 401, not 500; configure `JwtAuthenticationFilter` to catch `ExpiredJwtException`

---

### Task 1: Database layer — entities + Flyway migrations

**Files:**
- Create: `src/main/java/com/orderplatform/order/domain/OrderStatus.java`
- Create: `src/main/java/com/orderplatform/order/domain/Product.java`
- Create: `src/main/java/com/orderplatform/order/domain/Order.java`
- Create: `src/main/java/com/orderplatform/order/domain/OrderItem.java`
- Create: `src/main/resources/db/migration/V1__create_products.sql`
- Create: `src/main/resources/db/migration/V2__create_orders.sql`
- Create: `src/main/resources/db/migration/V3__create_order_items.sql`
- Create: `src/main/resources/db/migration/V4__create_indexes.sql`
- Create: `src/test/java/com/orderplatform/order/AbstractIntegrationTest.java`
- Create: `src/test/java/com/orderplatform/order/domain/FlywayMigrationTest.java`

**Interfaces:**
- Produces: `Product` entity with `id`, `name`, `description`, `price`, `stockQuantity`, `reservedQuantity`, `active`, `createdAt`, `updatedAt`
- Produces: `Order` entity with `id`, `correlationId`, `customerEmail`, `status` (`OrderStatus`), `totalAmount`, `createdAt`, `updatedAt`
- Produces: `OrderItem` entity with `id`, `order`, `productId` (UUID, no FK entity), `quantity`, `unitPrice`, `subtotal`
- Produces: `OrderStatus` enum: `PENDING`, `PAID`, `PAYMENT_FAILED`, `CANCELLED`
- Produces: `AbstractIntegrationTest` with `@Testcontainers`, `PostgreSQLContainer` and `RabbitMQContainer` as static fields with `@Container`

- [ ] **Step 1: Write migration SQL files**

`V1__create_products.sql`:
```sql
CREATE TABLE products (
  id UUID PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  description TEXT,
  price DECIMAL(10,2) NOT NULL,
  stock_quantity INTEGER NOT NULL CHECK (stock_quantity >= 0),
  reserved_quantity INTEGER NOT NULL DEFAULT 0,
  active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);
```

`V2__create_orders.sql`:
```sql
CREATE TABLE orders (
  id UUID PRIMARY KEY,
  correlation_id VARCHAR(36) NOT NULL,
  customer_email VARCHAR(255) NOT NULL,
  status VARCHAR(30) NOT NULL,
  total_amount DECIMAL(10,2) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);
```

`V3__create_order_items.sql`:
```sql
CREATE TABLE order_items (
  id UUID PRIMARY KEY,
  order_id UUID NOT NULL REFERENCES orders(id),
  product_id UUID NOT NULL,
  quantity INTEGER NOT NULL CHECK (quantity > 0),
  unit_price DECIMAL(10,2) NOT NULL,
  subtotal DECIMAL(10,2) NOT NULL
);
```

`V4__create_indexes.sql`:
```sql
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_customer_email ON orders(customer_email);
CREATE INDEX idx_orders_created_at ON orders(created_at DESC);
CREATE INDEX idx_order_items_order_id ON order_items(order_id);
```

- [ ] **Step 2: Write the failing migration test**

```java
@SpringBootTest
class FlywayMigrationTest extends AbstractIntegrationTest {
    @Autowired DataSource dataSource;

    @Test
    void migrationsShouldRunClean() throws Exception {
        try (var conn = dataSource.getConnection();
             var rs = conn.getMetaData().getTables(null, null, "products", null)) {
            assertThat(rs.next()).isTrue();
        }
    }
}
```

- [ ] **Step 3: Implement `AbstractIntegrationTest`**

Annotate with `@Testcontainers`. Declare `static PostgreSQLContainer<?> postgres` and `static RabbitMQContainer rabbit` with `@Container`. Use `@DynamicPropertySource` to override `spring.datasource.url`, `spring.datasource.username`, `spring.datasource.password`, `spring.rabbitmq.host`, `spring.rabbitmq.port`.

- [ ] **Step 4: Implement entity classes**

`OrderStatus`: plain enum with values `PENDING, PAID, PAYMENT_FAILED, CANCELLED`.

`Product`: `@Entity @Table("products")`. All fields map to snake_case columns. `createdAt`/`updatedAt` use `@Column(name="created_at")` etc. No Lombok — use standard Java fields with getters/setters.

`Order`: `@Entity @Table("orders")`. `status` mapped with `@Enumerated(EnumType.STRING)`. `items` mapped as `@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)`.

`OrderItem`: `@Entity @Table("order_items")`. `order` mapped as `@ManyToOne(fetch = FetchType.LAZY)`. `productId` is a plain `UUID` column (no `@ManyToOne` — different service boundary).

- [ ] **Step 5: Run test to verify it passes**

```bash
mvn test -pl services/order-service -Dtest=FlywayMigrationTest
```
Expected: PASS — Flyway runs all 4 migrations, table `products` exists.

- [ ] **Step 6: Commit**

```bash
git add services/order-service/src/main/resources/db/ services/order-service/src/main/java/com/orderplatform/order/domain/ services/order-service/src/test/
git commit -m "feat(order-service): add domain entities and Flyway migrations"
```

---

### Task 2: Repositories

**Files:**
- Create: `src/main/java/com/orderplatform/order/repository/ProductRepository.java`
- Create: `src/main/java/com/orderplatform/order/repository/OrderRepository.java`
- Test: `src/test/java/com/orderplatform/order/repository/ProductRepositoryTest.java`

**Interfaces:**
- Consumes: `Product`, `Order`, `OrderItem`, `OrderStatus` from Task 1
- Produces: `ProductRepository extends JpaRepository<Product, UUID>` with `findAllByActiveTrue(Pageable)`
- Produces: `OrderRepository extends JpaRepository<Order, UUID>` with `findAllByStatusAndCustomerEmailContainingIgnoreCase(OrderStatus, String, Pageable)` and `@Query("SELECT o FROM Order o JOIN FETCH o.items WHERE o.id = :id") Optional<Order> findByIdWithItems(@Param("id") UUID id)`

- [ ] **Step 1: Write failing repository test**

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
class ProductRepositoryTest extends AbstractIntegrationTest {
    @Autowired ProductRepository repo;

    @Test
    void saveAndFindProduct() {
        var p = new Product();
        p.setId(UUID.randomUUID());
        p.setName("Test");
        p.setPrice(new BigDecimal("99.90"));
        p.setStockQuantity(10);
        p.setActive(true);
        p.setCreatedAt(Instant.now());
        p.setUpdatedAt(Instant.now());
        repo.save(p);
        assertThat(repo.findById(p.getId())).isPresent();
    }
}
```

- [ ] **Step 2: Implement repository interfaces** — `ProductRepository`, `OrderRepository` with the query methods listed above.

- [ ] **Step 3: Run test to verify it passes**

```bash
mvn test -pl services/order-service -Dtest=ProductRepositoryTest
```

- [ ] **Step 4: Commit**

```bash
git commit -am "feat(order-service): add JPA repositories"
```

---

### Task 3: Correlation ID filter + JWT auth + Security config

**Files:**
- Create: `src/main/java/com/orderplatform/order/config/CorrelationIdFilter.java`
- Create: `src/main/java/com/orderplatform/order/config/JwtUtil.java`
- Create: `src/main/java/com/orderplatform/order/config/JwtAuthenticationFilter.java`
- Create: `src/main/java/com/orderplatform/order/config/SecurityConfig.java`
- Create: `src/main/java/com/orderplatform/order/controller/AuthController.java`
- Create: `src/main/java/com/orderplatform/order/dto/request/LoginRequest.java`
- Create: `src/main/java/com/orderplatform/order/dto/response/TokenResponse.java`
- Test: `src/test/java/com/orderplatform/order/controller/AuthControllerTest.java`

**Interfaces:**
- Produces: `CorrelationIdFilter implements Filter` — reads `X-Correlation-Id` header (generates UUID if absent), puts value in `MDC.put("correlationId", id)`, adds header to response, calls `filterChain.doFilter`
- Produces: `JwtUtil` — `generateToken(String email, List<String> roles): String`, `extractEmail(String token): String`, `isTokenValid(String token): boolean`; uses `Jwts.builder()` from JJWT 0.12.3; secret from `@Value("${app.jwt.secret}")`; expiry from `@Value("${app.jwt.expiration-ms}")`
- Produces: `JwtAuthenticationFilter extends OncePerRequestFilter` — extracts Bearer token, validates, sets `SecurityContextHolder`
- Produces: `SecurityConfig` — permits `POST /api/v1/auth/login` and `GET /api/v1/products**`; requires auth for everything else; stateless session; registers `JwtAuthenticationFilter` before `UsernamePasswordAuthenticationFilter`
- Produces: `POST /api/v1/auth/login` returning `TokenResponse(token: String, expiresIn: long)`; validates against `app.admin.email` / `app.admin.password` from properties; returns 401 on bad credentials

- [ ] **Step 1: Write failing auth test**

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
class AuthControllerTest extends AbstractIntegrationTest {
    @Autowired TestRestTemplate rest;

    @Test
    void loginWithValidCredentialsReturnsToken() {
        var req = new LoginRequest("admin@demo.com", "demo123");
        var resp = rest.postForEntity("/api/v1/auth/login", req, TokenResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().token()).isNotBlank();
    }

    @Test
    void loginWithBadCredentialsReturns401() {
        var req = new LoginRequest("admin@demo.com", "wrong");
        var resp = rest.postForEntity("/api/v1/auth/login", req, String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
```

- [ ] **Step 2: Implement `JwtUtil`** — `generateToken` builds JWT with subject=email, claim `roles`, issued-at and expiry; `isTokenValid` parses and checks expiry; sign with `Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))`.

- [ ] **Step 3: Implement `CorrelationIdFilter`** — `@Component`, `@Order(1)`. Read header, generate UUID if blank, `MDC.put`, set response header, `filterChain.doFilter`, `MDC.remove` in finally block.

- [ ] **Step 4: Implement `JwtAuthenticationFilter`** — extend `OncePerRequestFilter`. Extract `Authorization: Bearer {token}`, call `jwtUtil.isTokenValid`, build `UsernamePasswordAuthenticationToken` and set in `SecurityContextHolder`.

- [ ] **Step 5: Implement `SecurityConfig`** — `@Configuration @EnableWebSecurity`. Bean `SecurityFilterChain`. Disable CSRF (stateless). `sessionManagement(STATELESS)`. `addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)`.

- [ ] **Step 6: Implement `AuthController`** — `@RestController @RequestMapping("/api/v1/auth")`. `POST /login` takes `@Valid @RequestBody LoginRequest`. Compares email+password against configured values. Returns `TokenResponse` with generated token.

- [ ] **Step 7: Run tests**

```bash
mvn test -pl services/order-service -Dtest=AuthControllerTest
```
Expected: both tests PASS.

- [ ] **Step 8: Commit**

```bash
git commit -am "feat(order-service): add JWT auth, security config, correlation ID filter"
```

---

### Task 4: Product REST API

**Files:**
- Create: `src/main/java/com/orderplatform/order/dto/request/CreateProductRequest.java`
- Create: `src/main/java/com/orderplatform/order/dto/response/ProductResponse.java`
- Create: `src/main/java/com/orderplatform/order/service/ProductService.java`
- Create: `src/main/java/com/orderplatform/order/controller/ProductController.java`
- Create: `src/main/java/com/orderplatform/order/exception/GlobalExceptionHandler.java`
- Create: `src/main/java/com/orderplatform/order/exception/ResourceNotFoundException.java`
- Test: `src/test/java/com/orderplatform/order/controller/ProductControllerTest.java`

**Interfaces:**
- Consumes: `ProductRepository` from Task 2; `JwtUtil` from Task 3
- Produces: `CreateProductRequest` record: `@NotBlank String name`, `String description`, `@NotNull @Positive BigDecimal price`, `@NotNull @Min(0) Integer stockQuantity`
- Produces: `ProductResponse` record: `UUID id`, `String name`, `String description`, `BigDecimal price`, `Integer stockQuantity`, `boolean active`, `Instant createdAt`
- Produces: `ProductService` — `create(CreateProductRequest): ProductResponse`, `findAll(Pageable): Page<ProductResponse>`, `findById(UUID): ProductResponse`
- Produces: `POST /api/v1/products` → 201; `GET /api/v1/products` → 200 Page; `GET /api/v1/products/{id}` → 200 or 404
- Produces: `GlobalExceptionHandler @ControllerAdvice` handling `ResourceNotFoundException` → 404, `MethodArgumentNotValidException` → 422, generic `Exception` → 500; all returning the standard error format

- [ ] **Step 1: Write failing product API test**

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
class ProductControllerTest extends AbstractIntegrationTest {
    @Autowired TestRestTemplate rest;
    String token;

    @BeforeEach
    void login() {
        var resp = rest.postForEntity("/api/v1/auth/login",
            new LoginRequest("admin@demo.com", "demo123"), TokenResponse.class);
        token = resp.getBody().token();
    }

    @Test
    void createProductReturns201() {
        var req = new CreateProductRequest("Teclado", null, new BigDecimal("299.90"), 50);
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        var resp = rest.exchange("/api/v1/products", POST,
            new HttpEntity<>(req, headers), ProductResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody().id()).isNotNull();
        assertThat(resp.getBody().price()).isEqualByComparingTo("299.90");
    }

    @Test
    void getProductsReturnsPage() {
        var resp = rest.getForEntity("/api/v1/products?page=0&size=20", String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
```

- [ ] **Step 2: Implement `ResourceNotFoundException extends RuntimeException`** — takes `String message`.

- [ ] **Step 3: Implement `GlobalExceptionHandler`** — `@RestControllerAdvice`. Method for each exception type returns `ResponseEntity<Map<String,Object>>` with the standard error format; extract `correlationId` from `MDC.get("correlationId")`.

- [ ] **Step 4: Implement `ProductService @Service`** — `create`: set UUID, createdAt/updatedAt to `Instant.now()`, save, map to response. `findAll`: `productRepository.findAllByActiveTrue(pageable)` mapped to `Page<ProductResponse>`. `findById`: throw `ResourceNotFoundException` if not found.

- [ ] **Step 5: Implement `ProductController @RestController`** — `@RequestMapping("/api/v1/products")`. `POST` returns `ResponseEntity.status(201).body(...)`. `GET` returns `ResponseEntity.ok(...)`. `GET /{id}` returns `ResponseEntity.ok(...)`.

- [ ] **Step 6: Run tests**

```bash
mvn test -pl services/order-service -Dtest=ProductControllerTest
```
Expected: both tests PASS.

- [ ] **Step 7: Commit**

```bash
git commit -am "feat(order-service): add Product REST API"
```

---

### Task 5: Order REST API + State Machine

**Files:**
- Create: `src/main/java/com/orderplatform/order/dto/request/CreateOrderRequest.java`
- Create: `src/main/java/com/orderplatform/order/dto/request/OrderItemRequest.java`
- Create: `src/main/java/com/orderplatform/order/dto/response/OrderResponse.java`
- Create: `src/main/java/com/orderplatform/order/dto/response/OrderSummaryResponse.java`
- Create: `src/main/java/com/orderplatform/order/dto/response/OrderItemResponse.java`
- Create: `src/main/java/com/orderplatform/order/exception/InsufficientStockException.java`
- Create: `src/main/java/com/orderplatform/order/exception/InvalidOrderStateException.java`
- Create: `src/main/java/com/orderplatform/order/service/OrderService.java`
- Create: `src/main/java/com/orderplatform/order/controller/OrderController.java`
- Test: `src/test/java/com/orderplatform/order/controller/OrderControllerTest.java`

**Interfaces:**
- Consumes: `ProductRepository`, `OrderRepository` from Task 2; `OrderStatus` from Task 1
- Produces: `CreateOrderRequest` record: `@Email @NotBlank String customerEmail`, `@NotEmpty List<OrderItemRequest> items`
- Produces: `OrderItemRequest` record: `@NotNull UUID productId`, `@Min(1) Integer quantity`
- Produces: `OrderResponse` record: `UUID id`, `String status`, `BigDecimal totalAmount`, `String customerEmail`, `List<OrderItemResponse> items`, `Instant createdAt`, `Instant updatedAt`
- Produces: `OrderSummaryResponse` record: `UUID id`, `String status`, `BigDecimal totalAmount`, `String customerEmail`, `Instant createdAt` (no items — for list performance)
- Produces: `OrderService` — `create(CreateOrderRequest, String correlationId): OrderResponse`, `findAll(OrderStatus, String customerEmail, Pageable): Page<OrderSummaryResponse>`, `findById(UUID): OrderResponse`, `cancel(UUID): OrderResponse`, `transitionTo(UUID orderId, OrderStatus newStatus)` (called by RabbitMQ consumer in Task 7)
- Produces: `POST /api/v1/orders` → 201; `GET /api/v1/orders` → 200; `GET /api/v1/orders/{id}` → 200; `PATCH /api/v1/orders/{id}/cancel` → 200

- [ ] **Step 1: Write failing order tests**

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
class OrderControllerTest extends AbstractIntegrationTest {
    // ... (login in @BeforeEach, create a product in @BeforeEach)

    @Test
    void createOrderDecrementsStockAndReturnsPending() {
        // POST /api/v1/orders with 1 item (quantity 2)
        // assert status == PENDING
        // assert GET /api/v1/products/{id} stockQuantity decreased by 2
    }

    @Test
    void createOrderWithInsufficientStockReturns422() {
        // POST /api/v1/orders with quantity > available stock
        // assert 422 with error INSUFFICIENT_STOCK
    }

    @Test
    void cancelPendingOrderRestoresStock() {
        // create order, then PATCH /cancel
        // assert status == CANCELLED
        // assert stock restored
    }

    @Test
    void cancelPaidOrderReturns409() {
        // manually set order to PAID in DB, then PATCH /cancel
        // assert 409
    }
}
```

- [ ] **Step 2: Implement exception classes** — `InsufficientStockException(String productId, int available)` → mapped to 422 in `GlobalExceptionHandler`. `InvalidOrderStateException(String current, String attempted)` → mapped to 409.

- [ ] **Step 3: Implement `OrderService.create`** — in one `@Transactional`:
  1. For each item: load `Product` with pessimistic lock (`findById` + `@Lock(PESSIMISTIC_WRITE)`), check `stockQuantity - reservedQuantity >= quantity`, throw `InsufficientStockException` if not
  2. Decrement `stockQuantity` by quantity, save product
  3. Create `Order` (status=PENDING, correlationId from param, totalAmount=sum of subtotals)
  4. Create `OrderItem` for each (unitPrice = product.price at time of order)
  5. Save and return `OrderResponse`

- [ ] **Step 4: Implement `OrderService.cancel`** — load order, assert status is PENDING (throw `InvalidOrderStateException` if not), restore `stockQuantity` for each item's product, set status=CANCELLED, save.

- [ ] **Step 5: Implement `OrderService.transitionTo`** — load order; valid transitions: PENDING→PAID, PENDING→PAYMENT_FAILED; for PAYMENT_FAILED restore stock; throw `InvalidOrderStateException` for invalid transitions.

- [ ] **Step 6: Implement `OrderController`** — extract `X-Correlation-Id` header with `@RequestHeader(value = "X-Correlation-Id", required = false)` and pass to `orderService.create`.

- [ ] **Step 7: Run tests**

```bash
mvn test -pl services/order-service -Dtest=OrderControllerTest
```
Expected: all PASS.

- [ ] **Step 8: Commit**

```bash
git commit -am "feat(order-service): add Order REST API and state machine"
```

---

### Task 6: RabbitMQ topology + OrderCreated publisher

**Files:**
- Create: `src/main/java/com/orderplatform/order/config/RabbitConfig.java`
- Create: `src/main/java/com/orderplatform/order/messaging/event/OrderCreatedEvent.java`
- Create: `src/main/java/com/orderplatform/order/messaging/publisher/OrderEventPublisher.java`
- Modify: `src/main/java/com/orderplatform/order/service/OrderService.java` — call publisher after save
- Test: `src/test/java/com/orderplatform/order/messaging/OrderCreatedPublisherTest.java`

**Interfaces:**
- Consumes: `OrderService.create` from Task 5
- Produces: `OrderCreatedEvent` record: `UUID eventId`, `String correlationId`, `UUID orderId`, `String customerEmail`, `BigDecimal totalAmount`, `List<OrderCreatedEvent.Item> items`, `Instant createdAt`; inner record `Item(UUID productId, Integer quantity, BigDecimal unitPrice)`
- Produces: `RabbitConfig` — declares exchange `order.events` (TopicExchange, durable), queue `order.payment-result` (durable, with DLX args), binds it to `payment.events` exchange with routing key `payment.#`; also declares DLX `payment.events.dlx` and DLQ `order.payment-result.dlq`; configures `Jackson2JsonMessageConverter` as default converter
- Produces: `OrderEventPublisher` — `publishOrderCreated(Order order, String correlationId)`: builds `OrderCreatedEvent`, sends to exchange `order.events` with routing key `order.created`; sets `correlationId` in `MessageProperties`

- [ ] **Step 1: Write failing publisher test**

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
class OrderCreatedPublisherTest extends AbstractIntegrationTest {
    @Autowired RabbitTemplate rabbitTemplate;
    @Autowired OrderEventPublisher publisher;
    // create a minimal Order and call publisher.publishOrderCreated(order, "test-corr-id")
    // then receive from queue "payment.order-created" with rabbitTemplate.receive(queueName, 3000)
    // assert message is not null
    // assert message body deserializes to OrderCreatedEvent with correct orderId
}
```

- [ ] **Step 2: Add `payment.order-created` queue to `RabbitConfig`** — this queue is owned by payment-service but needs to exist for the test; in `RabbitConfig` declare it as a bean so it's created on startup.

- [ ] **Step 3: Implement `RabbitConfig`** — declare all exchanges, queues and bindings as Spring beans. Configure `RabbitTemplate` with `Jackson2JsonMessageConverter`. Configure `SimpleRabbitListenerContainerFactory` with same converter.

- [ ] **Step 4: Implement `OrderEventPublisher`** — `@Component`. Inject `RabbitTemplate`. Build event, convert correlationId to `MessageProperties`, call `rabbitTemplate.convertAndSend("order.events", "order.created", event, msg -> { msg.getMessageProperties().setCorrelationId(correlationId); return msg; })`.

- [ ] **Step 5: Modify `OrderService.create`** — after `orderRepository.save(order)`, call `orderEventPublisher.publishOrderCreated(order, correlationId)`.

- [ ] **Step 6: Run test**

```bash
mvn test -pl services/order-service -Dtest=OrderCreatedPublisherTest
```
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git commit -am "feat(order-service): add RabbitMQ config and OrderCreated publisher"
```

---

### Task 7: PaymentResult consumer + stock restoration

**Files:**
- Create: `src/main/java/com/orderplatform/order/messaging/event/PaymentApprovedEvent.java`
- Create: `src/main/java/com/orderplatform/order/messaging/event/PaymentFailedEvent.java`
- Create: `src/main/java/com/orderplatform/order/messaging/consumer/PaymentResultConsumer.java`
- Test: `src/test/java/com/orderplatform/order/messaging/PaymentResultConsumerTest.java`

**Interfaces:**
- Consumes: `OrderService.transitionTo` from Task 5; `RabbitConfig` queue `order.payment-result` from Task 6
- Produces: `PaymentApprovedEvent` record: `UUID eventId`, `String correlationId`, `UUID orderId`, `UUID paymentId`, `String customerEmail`, `BigDecimal totalAmount`, `Instant approvedAt`
- Produces: `PaymentFailedEvent` record: `UUID eventId`, `String correlationId`, `UUID orderId`, `UUID paymentId`, `String customerEmail`, `String reason`, `Instant failedAt`
- Produces: `PaymentResultConsumer` — `@RabbitListener(queues = "order.payment-result")` on `handlePaymentApproved(PaymentApprovedEvent)` and `handlePaymentFailed(PaymentFailedEvent)`; sets correlationId in MDC; calls `orderService.transitionTo`

- [ ] **Step 1: Write failing consumer test**

```java
@SpringBootTest
class PaymentResultConsumerTest extends AbstractIntegrationTest {
    @Autowired RabbitTemplate template;
    @Autowired OrderRepository orderRepository;
    // create a PENDING order in DB
    // send PaymentApprovedEvent to exchange "payment.events" with routing key "payment.approved"
    // await with Awaitility (5 seconds) until order status == PAID
    // assert stockQuantity was NOT restored (payment approved = stock consumed)
}
```

- [ ] **Step 2: Implement event records** — `PaymentApprovedEvent` and `PaymentFailedEvent` as Java records.

- [ ] **Step 3: Implement `PaymentResultConsumer`** — two methods annotated with `@RabbitListener`. Extract `correlationId` from `MessageProperties` and set in MDC. Call `orderService.transitionTo(event.orderId(), PAID)` or `transitionTo(event.orderId(), PAYMENT_FAILED)`. Remove MDC in finally block.

- [ ] **Step 4: Run test**

```bash
mvn test -pl services/order-service -Dtest=PaymentResultConsumerTest
```
Expected: PASS.

- [ ] **Step 5: Run full test suite**

```bash
mvn test -pl services/order-service
```
Expected: all tests PASS.

- [ ] **Step 6: Commit**

```bash
git commit -am "feat(order-service): add PaymentResult consumer — order-service complete"
```
