/*
 * Copyright 2026 ScopeFlow Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.scopeflow.mdc;

import io.scopeflow.core.ScopeFlow;
import io.scopeflow.core.Scope;
import io.scopeflow.core.ScopeFlowBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

@DisplayName("MdcPropagator")
class MdcPropagatorTest {

    private ScopeFlow scopeFlow;

    @BeforeEach
    void setUp() {
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("should copy allowed keys to MDC when scope opens")
    void copiesAllowedKeysToMdc() {
        MdcKeyPolicy policy = MdcKeyPolicy.of(Set.of("request.id", "tenant.id"));
        scopeFlow = ScopeFlowBuilder.create()
                .propagator(new MdcPropagator(policy))
                .build();

        try (Scope scope = scopeFlow.open("test",
                Map.of("request.id", "abc-123", "tenant.id", "acme"))) {

            assertThat(MDC.get("request.id")).isEqualTo("abc-123");
            assertThat(MDC.get("tenant.id")).isEqualTo("acme");
        }
    }

    @Test
    @DisplayName("should not copy non-allowed keys to MDC")
    void doesNotCopyNonAllowedKeys() {
        MdcKeyPolicy policy = MdcKeyPolicy.of(Set.of("request.id"));
        scopeFlow = ScopeFlowBuilder.create()
                .propagator(new MdcPropagator(policy))
                .build();

        try (Scope scope = scopeFlow.open("test",
                Map.of("request.id", "abc", "secret", "password123"))) {

            assertThat(MDC.get("request.id")).isEqualTo("abc");
            assertThat(MDC.get("secret")).isNull();
        }
    }

    @Test
    @DisplayName("should clear MDC keys when scope closes")
    void clearsMdcOnClose() {
        MdcKeyPolicy policy = MdcKeyPolicy.of(Set.of("request.id"));
        scopeFlow = ScopeFlowBuilder.create()
                .propagator(new MdcPropagator(policy))
                .build();

        try (Scope scope = scopeFlow.open("test",
                Map.of("request.id", "abc-123"))) {
            assertThat(MDC.get("request.id")).isEqualTo("abc-123");
        }

        assertThat(MDC.get("request.id")).isNull();
    }

    @Test
    @DisplayName("should update MDC on scope enrichment")
    void updatesMdcOnEnrichment() {
        MdcKeyPolicy policy = MdcKeyPolicy.of(Set.of("request.id", "user.id"));
        scopeFlow = ScopeFlowBuilder.create()
                .propagator(new MdcPropagator(policy))
                .build();

        try (Scope scope = scopeFlow.open("test")) {
            assertThat(MDC.get("request.id")).isNull();

            scope.put("request.id", "abc-123");
            assertThat(MDC.get("request.id")).isEqualTo("abc-123");

            scope.put("user.id", "user-456");
            assertThat(MDC.get("user.id")).isEqualTo("user-456");
        }

        assertThat(MDC.get("request.id")).isNull();
        assertThat(MDC.get("user.id")).isNull();
    }

    @Test
    @DisplayName("should support key prefix")
    void supportsKeyPrefix() {
        MdcKeyPolicy policy = MdcKeyPolicy.of(Set.of("request.id"), "sf.");
        scopeFlow = ScopeFlowBuilder.create()
                .propagator(new MdcPropagator(policy))
                .build();

        try (Scope scope = scopeFlow.open("test",
                Map.of("request.id", "abc-123"))) {

            assertThat(MDC.get("sf.request.id")).isEqualTo("abc-123");
            assertThat(MDC.get("request.id")).isNull();
        }

        assertThat(MDC.get("sf.request.id")).isNull();
    }

    @Test
    @DisplayName("allowAll policy should propagate all context keys")
    void allowAllPolicy() {
        MdcKeyPolicy policy = MdcKeyPolicy.allowAll();
        scopeFlow = ScopeFlowBuilder.create()
                .propagator(new MdcPropagator(policy))
                .build();

        try (Scope scope = scopeFlow.open("test",
                Map.of("a", "1", "b", "2", "c", "3"))) {

            assertThat(MDC.get("a")).isEqualTo("1");
            assertThat(MDC.get("b")).isEqualTo("2");
            assertThat(MDC.get("c")).isEqualTo("3");
        }

        assertThat(MDC.get("a")).isNull();
        assertThat(MDC.get("b")).isNull();
        assertThat(MDC.get("c")).isNull();
    }

    @Test
    @DisplayName("should preserve pre-existing MDC values")
    void preservesPreExistingMdcValues() {
        MdcKeyPolicy policy = MdcKeyPolicy.of(Set.of("request.id"));
        scopeFlow = ScopeFlowBuilder.create()
                .propagator(new MdcPropagator(policy))
                .build();

        MDC.put("request.id", "pre-existing");

        try (Scope scope = scopeFlow.open("test",
                Map.of("request.id", "new-value"))) {
            assertThat(MDC.get("request.id")).isEqualTo("new-value");
        }

        // Should restore the pre-existing value
        assertThat(MDC.get("request.id")).isEqualTo("pre-existing");
    }
}
