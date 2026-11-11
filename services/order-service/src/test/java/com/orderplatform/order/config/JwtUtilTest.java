package com.orderplatform.order.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        // Constructor injection — call directly instead of reflection
        jwtUtil = new JwtUtil("test-secret-that-is-at-least-32-bytes-long!!", 3600000L);
    }

    @Test
    void generateToken_returnsNonNullToken() {
        String token = jwtUtil.generateToken("user@test.com", List.of("ROLE_USER"));
        assertThat(token).isNotNull().isNotBlank();
    }

    @Test
    void extractEmail_returnsCorrectEmail() {
        String token = jwtUtil.generateToken("user@test.com", List.of());
        assertThat(jwtUtil.extractEmail(token)).isEqualTo("user@test.com");
    }

    @Test
    void isTokenValid_returnsTrueForValidToken() {
        String token = jwtUtil.generateToken("user@test.com", List.of());
        assertThat(jwtUtil.isTokenValid(token)).isTrue();
    }

    @Test
    void isTokenValid_returnsFalseForExpiredToken() throws InterruptedException {
        JwtUtil shortLived = new JwtUtil("test-secret-that-is-at-least-32-bytes-long!!", 1L);
        String token = shortLived.generateToken("user@test.com", List.of());
        Thread.sleep(10);
        assertThat(shortLived.isTokenValid(token)).isFalse();
    }

    @Test
    void isTokenValid_returnsFalseForGarbageToken() {
        assertThat(jwtUtil.isTokenValid("not.a.jwt.token")).isFalse();
    }
}
