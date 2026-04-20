/*
 * Copyright 2026 ScopeFlow Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.scopeflow.spring.autoconfigure;

import io.scopeflow.core.Scope;
import io.scopeflow.core.ScopeFlow;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Spring MVC {@link HandlerInterceptor} that opens a ScopeFlow scope for each
 * HTTP request and closes it after request completion.
 *
 * <p>The interceptor automatically:</p>
 * <ul>
 *   <li>Generates or extracts a request ID from the configured header</li>
 *   <li>Adds {@code request.id}, {@code http.method}, and {@code http.path} to the scope</li>
 *   <li>Sets the request ID in the response header for client correlation</li>
 *   <li>Closes the scope in {@code afterCompletion} (even on exceptions)</li>
 * </ul>
 *
 * @since 0.2.0
 */
public class ScopeFlowMvcInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ScopeFlowMvcInterceptor.class);
    private static final String SCOPE_ATTRIBUTE = ScopeFlowMvcInterceptor.class.getName() + ".scope";

    private final ScopeFlow scopeFlow;
    private final ScopeFlowProperties.Http.Server serverProps;

    public ScopeFlowMvcInterceptor(ScopeFlow scopeFlow, ScopeFlowProperties.Http.Server serverProps) {
        this.scopeFlow = scopeFlow;
        this.serverProps = serverProps;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Map<String, Object> values = new LinkedHashMap<>();

        // Generate or extract request ID
        if (serverProps.isGenerateRequestId()) {
            String headerName = serverProps.getRequestIdHeader();
            String requestId = request.getHeader(headerName);
            if (requestId == null || requestId.isBlank()) {
                requestId = UUID.randomUUID().toString();
            }
            values.put("request.id", requestId);
            response.setHeader(headerName, requestId);
        }

        // Add HTTP metadata
        values.put("http.method", request.getMethod());
        values.put("http.path", request.getRequestURI());

        // Open scope and store as request attribute
        Scope scope = scopeFlow.open("http.request", values);
        request.setAttribute(SCOPE_ATTRIBUTE, scope);

        log.debug("Opened request scope: {}", values);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        Object scopeObj = request.getAttribute(SCOPE_ATTRIBUTE);
        if (scopeObj instanceof Scope scope) {
            scope.close();
            request.removeAttribute(SCOPE_ATTRIBUTE);
            log.debug("Closed request scope");
        }
    }
}
