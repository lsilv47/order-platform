package com.orderplatform.order.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CorrelationIdFilterTest {

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    private CorrelationIdFilter filter;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
    }

    @Test
    void doFilter_generatesNewId_whenNoHeaderPresent() throws Exception {
        when(request.getHeader("X-Correlation-Id")).thenReturn(null);

        String[] capturedId = {null};
        doAnswer(inv -> { capturedId[0] = MDC.get("correlationId"); return null; })
                .when(filterChain).doFilter(any(), any());

        filter.doFilter(request, response, filterChain);

        assertThat(capturedId[0]).isNotNull();
        // must be a valid UUID
        assertThat(java.util.UUID.fromString(capturedId[0])).isNotNull();
        verify(response).setHeader("X-Correlation-Id", capturedId[0]);
    }

    @Test
    void doFilter_usesExistingId_whenValidUuidProvided() throws Exception {
        String validId = "550e8400-e29b-41d4-a716-446655440000";
        when(request.getHeader("X-Correlation-Id")).thenReturn(validId);

        String[] capturedId = {null};
        doAnswer(inv -> { capturedId[0] = MDC.get("correlationId"); return null; })
                .when(filterChain).doFilter(any(), any());

        filter.doFilter(request, response, filterChain);

        assertThat(capturedId[0]).isEqualTo(validId);
    }

    @Test
    void doFilter_generatesNewId_whenInvalidUuidProvided() throws Exception {
        when(request.getHeader("X-Correlation-Id")).thenReturn("not-a-uuid");

        String[] capturedId = {null};
        doAnswer(inv -> { capturedId[0] = MDC.get("correlationId"); return null; })
                .when(filterChain).doFilter(any(), any());

        filter.doFilter(request, response, filterChain);

        assertThat(capturedId[0]).isNotEqualTo("not-a-uuid");
        assertThat(java.util.UUID.fromString(capturedId[0])).isNotNull();
    }

    @Test
    void doFilter_clearsMdcInFinally() throws Exception {
        when(request.getHeader("X-Correlation-Id")).thenReturn(null);

        filter.doFilter(request, response, filterChain);

        assertThat(MDC.get("correlationId")).isNull();
    }
}
