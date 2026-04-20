/*
 * Copyright 2026 ScopeFlow Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.scopeflow.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

@DisplayName("DefaultScopeFlow")
class DefaultScopeFlowTest {

    private ScopeFlow scopeFlow;

    @BeforeEach
    void setUp() {
        scopeFlow = ScopeFlowBuilder.create().build();
    }

    @Nested
    @DisplayName("open/close")
    class OpenClose {

        @Test
        @DisplayName("should open a scope and make its context available")
        void openScopeExposesContext() {
            try (Scope scope = scopeFlow.open("test")) {
                scope.put("key", "value");

                assertThat(scopeFlow.currentContext().get("key"))
                        .hasValue("value");
            }
        }

        @Test
        @DisplayName("should clean context after scope closes")
        void contextCleanAfterClose() {
            try (Scope scope = scopeFlow.open("test")) {
                scope.put("key", "value");
            }

            assertThat(scopeFlow.currentContext().isEmpty()).isTrue();
        }

        @Test
        @DisplayName("should support scope with initial values")
        void openWithInitialValues() {
            try (Scope scope = scopeFlow.open("test",
                    Map.of("a", "1", "b", "2"))) {

                assertThat(scopeFlow.currentContext().get("a")).hasValue("1");
                assertThat(scopeFlow.currentContext().get("b")).hasValue("2");
            }
        }

        @Test
        @DisplayName("should reject null scope name")
        void rejectNullName() {
            assertThatNullPointerException()
                    .isThrownBy(() -> scopeFlow.open(null));
        }

        @Test
        @DisplayName("close should be idempotent")
        void closeIsIdempotent() {
            Scope scope = scopeFlow.open("test");
            scope.put("key", "value");
            scope.close();
            scope.close(); // second close should not throw

            assertThat(scopeFlow.currentContext().isEmpty()).isTrue();
        }
    }

    @Nested
    @DisplayName("put operations")
    class PutOperations {

        @Test
        @DisplayName("should support String values")
        void putString() {
            try (Scope scope = scopeFlow.open("test")) {
                scope.put("key", "hello");

                assertThat(scope.context().get("key")).hasValue("hello");
            }
        }

        @Test
        @DisplayName("should support Number values")
        void putNumber() {
            try (Scope scope = scopeFlow.open("test")) {
                scope.put("count", 42);

                assertThat(scope.context().get("count", Integer.class))
                        .hasValue(42);
            }
        }

        @Test
        @DisplayName("should support boolean values")
        void putBoolean() {
            try (Scope scope = scopeFlow.open("test")) {
                scope.put("active", true);

                assertThat(scope.context().get("active", Boolean.class))
                        .hasValue(true);
            }
        }

        @Test
        @DisplayName("should support putAll")
        void putAll() {
            try (Scope scope = scopeFlow.open("test")) {
                scope.putAll(Map.of("a", "1", "b", "2", "c", "3"));

                assertThat(scope.context().size()).isEqualTo(3);
                assertThat(scope.context().get("a")).hasValue("1");
                assertThat(scope.context().get("b")).hasValue("2");
                assertThat(scope.context().get("c")).hasValue("3");
            }
        }

        @Test
        @DisplayName("should support fluent chaining")
        void fluentChaining() {
            try (Scope scope = scopeFlow.open("test")
                    .put("a", "1")
                    .put("b", 2)
                    .put("c", true)) {

                assertThat(scope.context().size()).isEqualTo(3);
            }
        }

        @Test
        @DisplayName("should reject put after close")
        void rejectPutAfterClose() {
            Scope scope = scopeFlow.open("test");
            scope.close();

            assertThatIllegalStateException()
                    .isThrownBy(() -> scope.put("key", "value"))
                    .withMessageContaining("closed");
        }
    }

    @Nested
    @DisplayName("scope name")
    class ScopeName {

        @Test
        @DisplayName("should return the scope name")
        void returnsScopeName() {
            try (Scope scope = scopeFlow.open("http.request")) {
                assertThat(scope.name()).isEqualTo("http.request");
            }
        }
    }

    @Nested
    @DisplayName("convenience methods")
    class ConvenienceMethods {

        @Test
        @DisplayName("run() should open scope and execute task")
        void runMethod() {
            AtomicReference<String> captured = new AtomicReference<>();

            scopeFlow.run("test", Map.of("key", "value"), () -> {
                captured.set(scopeFlow.currentContext().get("key").orElse(null));
            });

            assertThat(captured.get()).isEqualTo("value");
            assertThat(scopeFlow.currentContext().isEmpty()).isTrue();
        }

        @Test
        @DisplayName("call() should open scope and return result")
        void callMethod() throws Exception {
            String result = scopeFlow.call("test", Map.of("key", "value"), () ->
                    scopeFlow.currentContext().get("key").orElse("missing"));

            assertThat(result).isEqualTo("value");
            assertThat(scopeFlow.currentContext().isEmpty()).isTrue();
        }

        @Test
        @DisplayName("run() should clean up context even on exception")
        void runCleansUpOnException() {
            assertThatRuntimeException().isThrownBy(() ->
                    scopeFlow.run("test", () -> {
                        throw new RuntimeException("boom");
                    })
            );

            assertThat(scopeFlow.currentContext().isEmpty()).isTrue();
        }

        @Test
        @DisplayName("call() should clean up context even on exception")
        void callCleansUpOnException() {
            assertThatExceptionOfType(Exception.class).isThrownBy(() ->
                    scopeFlow.call("test", () -> {
                        throw new Exception("boom");
                    })
            );

            assertThat(scopeFlow.currentContext().isEmpty()).isTrue();
        }
    }

    @Nested
    @DisplayName("key policy")
    class KeyPolicyTests {

        @Test
        @DisplayName("should filter keys based on allowList policy")
        void allowListFiltering() {
            ScopeFlow filtered = ScopeFlowBuilder.create()
                    .keyPolicy(ContextKeyPolicy.allowList(java.util.Set.of("allowed")))
                    .build();

            try (Scope scope = filtered.open("test",
                    Map.of("allowed", "yes", "denied", "no"))) {

                assertThat(scope.context().contains("allowed")).isTrue();
                assertThat(scope.context().contains("denied")).isFalse();
            }
        }

        @Test
        @DisplayName("should filter keys based on denyList policy")
        void denyListFiltering() {
            ScopeFlow filtered = ScopeFlowBuilder.create()
                    .keyPolicy(ContextKeyPolicy.denyList(java.util.Set.of("secret")))
                    .build();

            try (Scope scope = filtered.open("test",
                    Map.of("normal", "yes", "secret", "hidden"))) {

                assertThat(scope.context().contains("normal")).isTrue();
                assertThat(scope.context().contains("secret")).isFalse();
            }
        }
    }

    @Nested
    @DisplayName("propagator notifications")
    class PropagatorNotifications {

        @Test
        @DisplayName("should notify propagator on scope opened")
        void notifiesOnOpened() {
            var tracker = new TestPropagator();
            ScopeFlow sf = ScopeFlowBuilder.create()
                    .propagator(tracker)
                    .build();

            try (Scope scope = sf.open("test")) {
                assertThat(tracker.openedCount).isEqualTo(1);
                assertThat(tracker.lastOpenedName).isEqualTo("test");
            }
        }

        @Test
        @DisplayName("should notify propagator on scope enriched")
        void notifiesOnEnriched() {
            var tracker = new TestPropagator();
            ScopeFlow sf = ScopeFlowBuilder.create()
                    .propagator(tracker)
                    .build();

            try (Scope scope = sf.open("test")) {
                scope.put("key", "value");
                assertThat(tracker.enrichedCount).isEqualTo(1);
                assertThat(tracker.lastEnrichedKey).isEqualTo("key");
            }
        }

        @Test
        @DisplayName("should notify propagator on scope closed")
        void notifiesOnClosed() {
            var tracker = new TestPropagator();
            ScopeFlow sf = ScopeFlowBuilder.create()
                    .propagator(tracker)
                    .build();

            Scope scope = sf.open("test");
            scope.close();

            assertThat(tracker.closingCount).isEqualTo(1);
            assertThat(tracker.closedCount).isEqualTo(1);
        }
    }

    /**
     * Simple propagator for tracking lifecycle events in tests.
     */
    static class TestPropagator implements Propagator {
        int openedCount = 0;
        int enrichedCount = 0;
        int closingCount = 0;
        int closedCount = 0;
        String lastOpenedName;
        String lastEnrichedKey;

        @Override
        public String name() { return "test"; }

        @Override
        public void onScopeOpened(String scopeName, ScopeContext context) {
            openedCount++;
            lastOpenedName = scopeName;
        }

        @Override
        public void onScopeEnriched(String key, Object value, ScopeContext context) {
            enrichedCount++;
            lastEnrichedKey = key;
        }

        @Override
        public void onScopeClosing(String scopeName, ScopeContext context) {
            closingCount++;
        }

        @Override
        public void onScopeClosed(String scopeName, ScopeContext restoredContext) {
            closedCount++;
        }
    }
}
