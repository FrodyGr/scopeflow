/*
 * Copyright 2026 ScopeFlow Contributors
 */
package io.scopeflow.spring.autoconfigure;

import io.scopeflow.core.ScopeFlow;
import io.scopeflow.core.ScopeFlowBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ScopeFlowMvcInterceptor")
class ScopeFlowMvcInterceptorTest {

    private ScopeFlow scopeFlow;
    private ScopeFlowMvcInterceptor interceptor;
    private ScopeFlowProperties.Http.Server serverProps;

    @BeforeEach
    void setUp() {
        scopeFlow = ScopeFlowBuilder.create().build();
        serverProps = new ScopeFlowProperties.Http.Server();
        interceptor = new ScopeFlowMvcInterceptor(scopeFlow, serverProps);
    }

    @Test
    @DisplayName("should generate request.id when not present in header")
    void generatesRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());

        assertThat(scopeFlow.currentContext().get("request.id")).isPresent();
        assertThat(response.getHeader("X-Request-ID")).isNotNull();

        interceptor.afterCompletion(request, response, new Object(), null);
        assertThat(scopeFlow.currentContext().isEmpty()).isTrue();
    }

    @Test
    @DisplayName("should use request.id from header when present")
    void usesHeaderRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.addHeader("X-Request-ID", "custom-id-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());

        assertThat(scopeFlow.currentContext().get("request.id"))
                .hasValue("custom-id-123");
        assertThat(response.getHeader("X-Request-ID")).isEqualTo("custom-id-123");

        interceptor.afterCompletion(request, response, new Object(), null);
    }

    @Test
    @DisplayName("should add http.method and http.path")
    void addsHttpMetadata() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/orders/123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());

        assertThat(scopeFlow.currentContext().get("http.method")).hasValue("PUT");
        assertThat(scopeFlow.currentContext().get("http.path")).hasValue("/api/orders/123");

        interceptor.afterCompletion(request, response, new Object(), null);
    }

    @Test
    @DisplayName("should clean up context after afterCompletion")
    void cleansUpOnCompletion() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());
        assertThat(scopeFlow.currentContext().isEmpty()).isFalse();

        interceptor.afterCompletion(request, response, new Object(), null);
        assertThat(scopeFlow.currentContext().isEmpty()).isTrue();
    }

    @Test
    @DisplayName("should clean up context even on exception")
    void cleansUpOnException() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());
        interceptor.afterCompletion(request, response, new Object(),
                new RuntimeException("simulated error"));

        assertThat(scopeFlow.currentContext().isEmpty()).isTrue();
    }

    @Test
    @DisplayName("should use custom request ID header")
    void customRequestIdHeader() throws Exception {
        serverProps.setRequestIdHeader("X-Correlation-ID");
        interceptor = new ScopeFlowMvcInterceptor(scopeFlow, serverProps);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
        request.addHeader("X-Correlation-ID", "corr-456");
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());

        assertThat(scopeFlow.currentContext().get("request.id"))
                .hasValue("corr-456");
        assertThat(response.getHeader("X-Correlation-ID")).isEqualTo("corr-456");

        interceptor.afterCompletion(request, response, new Object(), null);
    }

    @Test
    @DisplayName("should not generate request.id when disabled")
    void requestIdGenerationDisabled() throws Exception {
        serverProps.setGenerateRequestId(false);
        interceptor = new ScopeFlowMvcInterceptor(scopeFlow, serverProps);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());

        assertThat(scopeFlow.currentContext().contains("request.id")).isFalse();
        assertThat(scopeFlow.currentContext().contains("http.method")).isTrue();

        interceptor.afterCompletion(request, response, new Object(), null);
    }
}
