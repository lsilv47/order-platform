package com.orderplatform.order.controller;

import com.orderplatform.order.AbstractIntegrationTest;
import com.orderplatform.order.dto.request.LoginRequest;
import com.orderplatform.order.dto.response.TokenResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthControllerTest extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate rest;

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
