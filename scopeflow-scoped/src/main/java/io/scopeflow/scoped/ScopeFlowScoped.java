/*
 * Copyright 2026 ScopeFlow Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.scopeflow.scoped;

import io.scopeflow.core.Propagator;
import io.scopeflow.core.Scope;
import io.scopeflow.core.ScopeContext;
import io.scopeflow.core.ScopeFlow;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.StructuredTaskScope;
import java.util.List;
import java.util.ArrayList;

/**
 * ScopedValue and StructuredTaskScope integration for ScopeFlow.
 *
 * <p>This class provides two key features:</p>
 * <ol>
 *   <li><strong>ScopedValue-based context</strong>: An alternative to ThreadLocal-based
 *       scoping with better performance for virtual threads. Context is bound using
 *       {@link ScopedValue#runWhere}/{@link ScopedValue#callWhere} for structured nesting.</li>
 *   <li><strong>StructuredTaskScope execution</strong>: Execute multiple tasks concurrently
 *       with automatic context propagation using Java 21's {@link StructuredTaskScope}.</li>
 * </ol>
 *
 * <h2>ScopedValue Usage</h2>
 * <pre>{@code
 * ScopeFlowScoped.run(scopeFlow, "order.process",
 *     Map.of("order.id", "ORD-123"), () -> {
 *         // Context available via both ScopeFlowScoped.context() and scopeFlow.currentContext()
 *         String orderId = ScopeFlowScoped.context().get("order.id").orElse("?");
 *         log.info("Processing order {}", orderId);
 *     });
 * }</pre>
 *
 * <h2>StructuredTaskScope Usage</h2>
 * <pre>{@code
 * List<String> results = ScopeFlowScoped.executeAll(scopeFlow, List.of(
 *     () -> fetchFromServiceA(),
 *     () -> fetchFromServiceB(),
 *     () -> fetchFromServiceC()
 * ));
 * }</pre>
 *
 * @since 0.4.0
 */
public final class ScopeFlowScoped {

    private static final ScopedValue<Map<String, Object>> SCOPED_CONTEXT = ScopedValue.newInstance();

    private ScopeFlowScoped() {
    }

    /**
     * Returns the current ScopedValue-based context, or an empty map if none is bound.
     */
    public static Map<String, Object> contextMap() {
        return SCOPED_CONTEXT.orElse(Collections.emptyMap());
    }

    /**
     * Returns the current context as a {@link ScopeContext} view.
     */
    public static ScopeContext context() {
        return ScopeContext.of(contextMap());
    }

    /**
     * Executes a task within a ScopedValue-bound context, also opening a ScopeFlow scope
     * to trigger propagators (MDC, OTel, etc.).
     *
     * @param scopeFlow the ScopeFlow instance
     * @param name      scope name
     * @param values    context values
     * @param task      the task to execute
     */
    public static void run(ScopeFlow scopeFlow, String name, Map<String, ?> values, Runnable task) {
        Objects.requireNonNull(scopeFlow, "scopeFlow must not be null");
        Objects.requireNonNull(task, "task must not be null");

        Map<String, Object> parent = SCOPED_CONTEXT.orElse(Collections.emptyMap());
        Map<String, Object> merged = new LinkedHashMap<>(parent);
        merged.putAll(values);
        Map<String, Object> unmodifiable = Collections.unmodifiableMap(merged);

        ScopedValue.runWhere(SCOPED_CONTEXT, unmodifiable, () -> {
            try (Scope scope = scopeFlow.open(name, values)) {
                task.run();
            }
        });
    }

    /**
     * Executes a callable within a ScopedValue-bound context, also opening a ScopeFlow scope.
     *
     * @param scopeFlow the ScopeFlow instance
     * @param name      scope name
     * @param values    context values
     * @param task      the callable to execute
     * @return the callable's result
     * @throws Exception if the callable throws
     */
    public static <T> T call(ScopeFlow scopeFlow, String name, Map<String, ?> values,
                              Callable<T> task) throws Exception {
        Objects.requireNonNull(scopeFlow, "scopeFlow must not be null");
        Objects.requireNonNull(task, "task must not be null");

        Map<String, Object> parent = SCOPED_CONTEXT.orElse(Collections.emptyMap());
        Map<String, Object> merged = new LinkedHashMap<>(parent);
        merged.putAll(values);
        Map<String, Object> unmodifiable = Collections.unmodifiableMap(merged);

        return ScopedValue.callWhere(SCOPED_CONTEXT, unmodifiable, () -> {
            try (Scope scope = scopeFlow.open(name, values)) {
                return task.call();
            }
        });
    }

    /**
     * Executes multiple tasks concurrently using {@link StructuredTaskScope},
     * automatically propagating the current ScopeFlow context to each forked task.
     *
     * <p>All tasks must succeed. If any task fails, all others are interrupted
     * and the exception is propagated.</p>
     *
     * @param scopeFlow the ScopeFlow instance for context wrapping
     * @param tasks     the tasks to execute concurrently
     * @return list of results in the same order as the input tasks
     * @throws Exception if any task fails
     */
    @SuppressWarnings("preview")
    public static <T> List<T> executeAll(ScopeFlow scopeFlow, List<Callable<T>> tasks)
            throws Exception {
        Objects.requireNonNull(scopeFlow, "scopeFlow must not be null");
        Objects.requireNonNull(tasks, "tasks must not be null");

        try (var taskScope = new StructuredTaskScope.ShutdownOnFailure()) {
            List<StructuredTaskScope.Subtask<T>> subtasks = new ArrayList<>();
            for (Callable<T> task : tasks) {
                subtasks.add(taskScope.fork(scopeFlow.wrap(task)));
            }
            taskScope.join().throwIfFailed();
            return subtasks.stream()
                    .map(StructuredTaskScope.Subtask::get)
                    .toList();
        }
    }
}
