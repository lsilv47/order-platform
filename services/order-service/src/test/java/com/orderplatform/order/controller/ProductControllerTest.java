package com.orderplatform.order.controller;

import com.orderplatform.order.AbstractIntegrationTest;
import com.orderplatform.order.dto.request.CreateProductRequest;
import com.orderplatform.order.dto.request.LoginRequest;
import com.orderplatform.order.dto.response.ProductResponse;
import com.orderplatform.order.dto.response.TokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.HttpMethod.POST;

@SpringBootTest(webEnvironment = RANDOM_PORT)
class ProductControllerTest extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate rest;

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
