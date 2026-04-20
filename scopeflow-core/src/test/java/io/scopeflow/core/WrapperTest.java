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

import io.scopeflow.core.wrap.ContextCallable;
import io.scopeflow.core.wrap.ContextExecutor;
import io.scopeflow.core.wrap.ContextExecutorService;
import io.scopeflow.core.wrap.ContextRunnable;
import io.scopeflow.core.wrap.ContextSupplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Task Wrappers")
class WrapperTest {

    private ScopeFlow scopeFlow;

    @BeforeEach
    void setUp() {
        scopeFlow = ScopeFlowBuilder.create().build();
    }

    @Nested
    @DisplayName("wrap(Runnable)")
    class RunnableWrapper {

        @Test
        @DisplayName("should propagate context to the wrapped runnable")
        void propagatesContext() throws Exception {
            AtomicReference<String> captured = new AtomicReference<>();

            try (Scope scope = scopeFlow.open("test", Map.of("key", "value"))) {
                Runnable wrapped = scopeFlow.wrap(() ->
                        captured.set(scopeFlow.currentContext().get("key").orElse("missing")));

                // Execute on a different thread
                Thread.ofVirtual().start(wrapped).join();
            }

            assertThat(captured.get()).isEqualTo("value");
        }

        @Test
        @DisplayName("should clean up context after execution")
        void cleansUpContext() throws Exception {
            AtomicReference<Boolean> emptyAfter = new AtomicReference<>();

            try (Scope scope = scopeFlow.open("test", Map.of("key", "value"))) {
                Runnable wrapped = scopeFlow.wrap(() -> {
                    // context should be active during execution
                });

                Thread t = Thread.ofVirtual().start(() -> {
                    wrapped.run();
                    emptyAfter.set(scopeFlow.currentContext().isEmpty());
                });
                t.join();
            }

            assertThat(emptyAfter.get()).isTrue();
        }

        @Test
        @DisplayName("should propagate empty context when no scope is active")
        void propagatesEmptyContext() throws Exception {
            AtomicReference<Boolean> isEmpty = new AtomicReference<>();

            Runnable wrapped = scopeFlow.wrap(() ->
                    isEmpty.set(scopeFlow.currentContext().isEmpty()));

            Thread.ofVirtual().start(wrapped).join();

            assertThat(isEmpty.get()).isTrue();
        }
    }

    @Nested
    @DisplayName("wrap(Callable)")
    class CallableWrapper {

        @Test
        @DisplayName("should propagate context and return result")
        void propagatesContextAndReturnsResult() throws Exception {
            try (Scope scope = scopeFlow.open("test", Map.of("key", "value"))) {
                Callable<String> callable = () ->
                        scopeFlow.currentContext().get("key").orElse("missing");
                Callable<String> wrapped = scopeFlow.wrap(callable);

                ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
                Future<String> future = executor.submit(wrapped);
                String result = future.get();
                executor.shutdown();

                assertThat(result).isEqualTo("value");
            }
        }
    }

    @Nested
    @DisplayName("wrap(Supplier)")
    class SupplierWrapper {

        @Test
        @DisplayName("should propagate context and return value")
        void propagatesContextAndReturnsValue() throws Exception {
            AtomicReference<String> result = new AtomicReference<>();

            try (Scope scope = scopeFlow.open("test", Map.of("key", "value"))) {
                java.util.function.Supplier<String> supplier = () ->
                        scopeFlow.currentContext().get("key").orElse("missing");
                java.util.function.Supplier<String> wrapped = scopeFlow.wrap(supplier);

                Thread.ofVirtual().start(() -> result.set(wrapped.get())).join();
            }

            assertThat(result.get()).isEqualTo("value");
        }
    }

    @Nested
    @DisplayName("ContextRunnable")
    class ContextRunnableTest {

        @Test
        @DisplayName("should restore snapshot in run()")
        void restoresSnapshot() throws Exception {
            AtomicReference<String> captured = new AtomicReference<>();

            try (Scope scope = scopeFlow.open("test", Map.of("key", "value"))) {
                ScopeSnapshot snapshot = scopeFlow.capture();
                ContextRunnable cr = new ContextRunnable(
                        () -> captured.set(scopeFlow.currentContext().get("key").orElse("missing")),
                        snapshot
                );

                Thread.ofVirtual().start(cr).join();
            }

            assertThat(captured.get()).isEqualTo("value");
        }
    }

    @Nested
    @DisplayName("ContextCallable")
    class ContextCallableTest {

        @Test
        @DisplayName("should restore snapshot in call()")
        void restoresSnapshot() throws Exception {
            try (Scope scope = scopeFlow.open("test", Map.of("key", "value"))) {
                ScopeSnapshot snapshot = scopeFlow.capture();
                ContextCallable<String> cc = new ContextCallable<>(
                        () -> scopeFlow.currentContext().get("key").orElse("missing"),
                        snapshot
                );

                ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
                String result = executor.submit(cc).get();
                executor.shutdown();

                assertThat(result).isEqualTo("value");
            }
        }
    }

    @Nested
    @DisplayName("ContextExecutor")
    class ContextExecutorTest {

        @Test
        @DisplayName("should wrap tasks automatically")
        void wrapsTasksAutomatically() throws Exception {
            AtomicReference<String> captured = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);

            try (Scope scope = scopeFlow.open("test", Map.of("key", "value"))) {
                Executor innerExecutor = Executors.newVirtualThreadPerTaskExecutor();
                ContextExecutor contextExecutor = new ContextExecutor(innerExecutor, scopeFlow);

                contextExecutor.execute(() -> {
                    captured.set(scopeFlow.currentContext().get("key").orElse("missing"));
                    latch.countDown();
                });

                latch.await(5, TimeUnit.SECONDS);
            }

            assertThat(captured.get()).isEqualTo("value");
        }
    }

    @Nested
    @DisplayName("ContextExecutorService")
    class ContextExecutorServiceTest {

        @Test
        @DisplayName("should propagate context via submit(Callable)")
        void submitCallable() throws Exception {
            try (Scope scope = scopeFlow.open("test", Map.of("key", "value"))) {
                ExecutorService inner = Executors.newVirtualThreadPerTaskExecutor();
                ContextExecutorService wrapped = new ContextExecutorService(inner, scopeFlow);

                Future<String> result = wrapped.submit(() ->
                        scopeFlow.currentContext().get("key").orElse("missing"));

                assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo("value");
                wrapped.shutdown();
            }
        }

        @Test
        @DisplayName("should propagate context via submit(Runnable)")
        void submitRunnable() throws Exception {
            AtomicReference<String> captured = new AtomicReference<>();

            try (Scope scope = scopeFlow.open("test", Map.of("key", "value"))) {
                ExecutorService inner = Executors.newVirtualThreadPerTaskExecutor();
                ContextExecutorService wrapped = new ContextExecutorService(inner, scopeFlow);

                wrapped.submit(() ->
                        captured.set(scopeFlow.currentContext().get("key").orElse("missing"))
                ).get(5, TimeUnit.SECONDS);

                wrapped.shutdown();
            }

            assertThat(captured.get()).isEqualTo("value");
        }
    }
}
