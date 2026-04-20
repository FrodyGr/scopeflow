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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@DisplayName("MDC Cleanup")
class MdcCleanupTest {

    private ScopeFlow scopeFlow;

    @BeforeEach
    void setUp() {
        MDC.clear();
        MdcKeyPolicy policy = MdcKeyPolicy.of(Set.of("request.id", "tenant.id", "key"));
        scopeFlow = ScopeFlowBuilder.create()
                .propagator(new MdcPropagator(policy))
                .build();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("should clean up MDC even when scope body throws exception")
    void cleansUpOnException() {
        try {
            try (Scope scope = scopeFlow.open("test",
                    Map.of("request.id", "abc-123"))) {
                assertThat(MDC.get("request.id")).isEqualTo("abc-123");
                throw new RuntimeException("boom");
            }
        } catch (RuntimeException ignored) {
            // expected
        }

        assertThat(MDC.get("request.id")).isNull();
    }

    @Test
    @DisplayName("nested scopes should restore MDC correctly")
    void nestedScopesRestoreMdcCorrectly() {
        try (Scope outer = scopeFlow.open("outer",
                Map.of("request.id", "outer-id"))) {
            assertThat(MDC.get("request.id")).isEqualTo("outer-id");

            try (Scope inner = scopeFlow.open("inner")) {
                inner.put("request.id", "inner-id");
                assertThat(MDC.get("request.id")).isEqualTo("inner-id");
            }

            // Outer value restored
            assertThat(MDC.get("request.id")).isEqualTo("outer-id");
        }

        assertThat(MDC.get("request.id")).isNull();
    }

    @Test
    @DisplayName("three-level nested scopes should restore MDC at each level")
    void threeLevelNestedScopes() {
        try (Scope s1 = scopeFlow.open("s1", Map.of("key", "v1"))) {
            assertThat(MDC.get("key")).isEqualTo("v1");

            try (Scope s2 = scopeFlow.open("s2")) {
                s2.put("key", "v2");
                assertThat(MDC.get("key")).isEqualTo("v2");

                try (Scope s3 = scopeFlow.open("s3")) {
                    s3.put("key", "v3");
                    assertThat(MDC.get("key")).isEqualTo("v3");
                }

                assertThat(MDC.get("key")).isEqualTo("v2");
            }

            assertThat(MDC.get("key")).isEqualTo("v1");
        }

        assertThat(MDC.get("key")).isNull();
    }

    @Test
    @DisplayName("MDC isolation between virtual threads")
    void mdcIsolationBetweenVirtualThreads() throws Exception {
        int threadCount = 500;
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<String> errors = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            final String threadId = "vt-" + i;

            Runnable wrapped = scopeFlow.wrap(() -> {
                try {
                    try (Scope scope = scopeFlow.open("task",
                            Map.of("request.id", threadId))) {

                        String mdcValue = MDC.get("request.id");
                        if (!threadId.equals(mdcValue)) {
                            errors.add("Expected MDC " + threadId
                                    + " but got " + mdcValue);
                        }

                        Thread.sleep(1); // simulate work
                    }

                    // MDC should be clean after scope close
                    String afterClose = MDC.get("request.id");
                    if (afterClose != null) {
                        errors.add(threadId + ": MDC not cleaned, got " + afterClose);
                    }
                } catch (Exception e) {
                    errors.add(threadId + ": " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });

            Thread.ofVirtual().start(wrapped);
        }

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        assertThat(completed).as("All threads should complete").isTrue();
        assertThat(errors).as("No MDC contamination").isEmpty();
    }

    @Test
    @DisplayName("propagated context should set MDC in target thread")
    void propagatedContextSetsMdcInTargetThread() throws Exception {
        try (Scope scope = scopeFlow.open("parent",
                Map.of("request.id", "parent-req"))) {

            java.util.concurrent.atomic.AtomicReference<String> captured =
                    new java.util.concurrent.atomic.AtomicReference<>();

            Runnable wrapped = scopeFlow.wrap(() ->
                    captured.set(MDC.get("request.id")));

            Thread.ofVirtual().start(wrapped).join();

            assertThat(captured.get()).isEqualTo("parent-req");
        }
    }
}
