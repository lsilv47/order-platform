package com.orderplatform.order.controller;

import com.orderplatform.order.AbstractIntegrationTest;
import com.orderplatform.order.domain.Order;
import com.orderplatform.order.domain.OrderStatus;
import com.orderplatform.order.dto.request.CreateOrderRequest;
import com.orderplatform.order.dto.request.CreateProductRequest;
import com.orderplatform.order.dto.request.LoginRequest;
import com.orderplatform.order.dto.request.OrderItemRequest;
import com.orderplatform.order.dto.response.OrderResponse;
import com.orderplatform.order.dto.response.ProductResponse;
import com.orderplatform.order.dto.response.TokenResponse;
import com.orderplatform.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.POST;

@SpringBootTest(webEnvironment = RANDOM_PORT)
class OrderControllerTest extends AbstractIntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired OrderRepository orderRepository;

    String token;
    UUID productId;

    @BeforeEach
    void setup() {
        var loginResp = rest.postForEntity("/api/v1/auth/login",
                new LoginRequest("admin@demo.com", "demo123"), TokenResponse.class);
        token = loginResp.getBody().token();

        var headers = authHeaders();
        var req = new CreateProductRequest("TestProduct", null, new BigDecimal("10.00"), 100);
        var resp = rest.exchange("/api/v1/products", POST,
                new HttpEntity<>(req, headers), ProductResponse.class);
        productId = resp.getBody().id();
    }

    @Test
    void createOrderDecrementsStockAndReturnsPending() {
        int stockBefore = getStock();
        OrderResponse order = createOrder(2);

        assertThat(order.status()).isEqualTo("PENDING");
        assertThat(getStock()).isEqualTo(stockBefore - 2);
    }

    @Test
    void createOrderWithInsufficientStockReturns422() {
        var resp = rest.exchange("/api/v1/orders", POST,
                new HttpEntity<>(new CreateOrderRequest("customer@test.com",
                        List.of(new OrderItemRequest(productId, 99999))), authHeaders()),
                String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void cancelPendingOrderRestoresStock() {
        int stockBefore = getStock();
        OrderResponse order = createOrder(5);

        var cancel = rest.exchange("/api/v1/orders/" + order.id() + "/cancel",
                PATCH, new HttpEntity<>(authHeaders()), OrderResponse.class);
        assertThat(cancel.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancel.getBody().status()).isEqualTo("CANCELLED");
        assertThat(getStock()).isEqualTo(stockBefore);
    }

    @Test
    void cancelPaidOrderReturns409() {
        OrderResponse order = createOrder(1);

        // manually set to PAID in the DB
        Order o = orderRepository.findById(order.id()).orElseThrow();
        o.setStatus(OrderStatus.PAID);
        o.setUpdatedAt(Instant.now());
        orderRepository.save(o);

        var cancel = rest.exchange("/api/v1/orders/" + order.id() + "/cancel",
                PATCH, new HttpEntity<>(authHeaders()), String.class);
        assertThat(cancel.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // --- helpers ---

    private HttpHeaders authHeaders() {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private int getStock() {
        return rest.getForEntity("/api/v1/products/" + productId, ProductResponse.class)
                .getBody().stockQuantity();
    }

    private OrderResponse createOrder(int quantity) {
        var resp = rest.exchange("/api/v1/orders", POST,
                new HttpEntity<>(new CreateOrderRequest("customer@test.com",
                        List.of(new OrderItemRequest(productId, quantity))), authHeaders()),
                OrderResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resp.getBody();
    }
}
