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
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Concurrency Isolation")
class ConcurrencyIsolationTest {

    private ScopeFlow scopeFlow;

    @BeforeEach
    void setUp() {
        scopeFlow = ScopeFlowBuilder.create().build();
    }

    @Test
    @DisplayName("10K virtual threads should have fully isolated contexts")
    void tenThousandVirtualThreadsAreIsolated() throws Exception {
        int threadCount = 10_000;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<String> errors = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            final String threadId = "thread-" + i;
            Thread.ofVirtual().name(threadId).start(() -> {
                try {
                    startLatch.await(); // all threads start together

                    // Each thread opens its own scope
                    try (Scope scope = scopeFlow.open("worker",
                            Map.of("thread.id", threadId))) {

                        // Simulate some work
                        Thread.sleep(1);

                        // Verify context is correct for this thread
                        String value = scopeFlow.currentContext()
                                .get("thread.id")
                                .orElse("MISSING");

                        if (!threadId.equals(value)) {
                            errors.add("Expected " + threadId + " but got " + value);
                        }
                    }

                    // Verify context is clean after scope closes
                    if (!scopeFlow.currentContext().isEmpty()) {
                        errors.add(threadId + ": context not empty after close");
                    }

                } catch (Exception e) {
                    errors.add(threadId + ": " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Release all threads simultaneously
        startLatch.countDown();

        // Wait for all threads to complete
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        assertThat(completed).as("All threads should complete within 30s").isTrue();
        assertThat(errors).as("No context contamination should occur").isEmpty();
    }

    @Test
    @DisplayName("context propagation to virtual threads should be isolated")
    void propagatedContextIsIsolated() throws Exception {
        int threadCount = 1_000;
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<String> errors = Collections.synchronizedList(new ArrayList<>());

        try (Scope parent = scopeFlow.open("parent", Map.of("shared", "value"))) {
            for (int i = 0; i < threadCount; i++) {
                final String taskId = "task-" + i;
                Runnable wrapped = scopeFlow.wrap(() -> {
                    try {
                        // Add task-specific context
                        try (Scope taskScope = scopeFlow.open("task")) {
                            taskScope.put("task.id", taskId);

                            // Verify both shared and task-specific values
                            ScopeContext ctx = scopeFlow.currentContext();
                            String shared = ctx.get("shared").orElse("MISSING");
                            String task = ctx.get("task.id").orElse("MISSING");

                            if (!"value".equals(shared)) {
                                errors.add(taskId + ": shared value wrong: " + shared);
                            }
                            if (!taskId.equals(task)) {
                                errors.add(taskId + ": task.id wrong: " + task);
                            }
                        }
                    } catch (Exception e) {
                        errors.add(taskId + ": " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                });

                Thread.ofVirtual().start(wrapped);
            }

            boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
            assertThat(completed).as("All tasks should complete").isTrue();
            assertThat(errors).as("No context contamination").isEmpty();
        }
    }

    @Test
    @DisplayName("concurrent scope opens and closes should not interfere")
    void concurrentOpenCloseNoInterference() throws Exception {
        int iterations = 5_000;
        CountDownLatch doneLatch = new CountDownLatch(iterations);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < iterations; i++) {
            final int idx = i;
            Thread.ofVirtual().start(() -> {
                try {
                    try (Scope s1 = scopeFlow.open("s1", Map.of("idx", String.valueOf(idx)))) {
                        try (Scope s2 = scopeFlow.open("s2", Map.of("nested", "true"))) {
                            String val = scopeFlow.currentContext()
                                    .get("idx")
                                    .orElse("MISSING");
                            if (!String.valueOf(idx).equals(val)) {
                                failureCount.incrementAndGet();
                            }
                        }
                    }

                    if (!scopeFlow.currentContext().isEmpty()) {
                        failureCount.incrementAndGet();
                    }
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        assertThat(failureCount.get()).isZero();
    }
}
