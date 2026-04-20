/*
 * Copyright 2026 ScopeFlow Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.scopeflow.micrometer;

import io.micrometer.context.ThreadLocalAccessor;
import io.scopeflow.core.Scope;
import io.scopeflow.core.ScopeContext;
import io.scopeflow.core.ScopeFlow;

import java.util.Map;
import java.util.Objects;

/**
 * Micrometer {@link ThreadLocalAccessor} that bridges ScopeFlow context into
 * Micrometer's context propagation framework.
 *
 * <p>This enables ScopeFlow context to flow through reactive pipelines
 * (Project Reactor) and other frameworks that use Micrometer's context
 * propagation. Register this accessor with Micrometer's
 * {@code ContextRegistry}:</p>
 *
 * <pre>{@code
 * ContextRegistry.getInstance()
 *     .registerThreadLocalAccessor(new ScopeFlowThreadLocalAccessor(scopeFlow));
 * }</pre>
 *
 * <p>Lifecycle:</p>
 * <ul>
 *   <li>{@code getValue()} — captures the current scope context as an immutable map</li>
 *   <li>{@code setValue(Map)} — opens a scope named {@code "micrometer.restore"} with the captured values</li>
 *   <li>{@code setValue()} — closes any scope opened by {@code setValue(Map)}, restoring the previous context</li>
 * </ul>
 *
 * @since 0.3.0
 */
public class ScopeFlowThreadLocalAccessor implements ThreadLocalAccessor<Map<String, Object>> {

    /** The key used to identify ScopeFlow context in Micrometer's context registry. */
    public static final String KEY = "scopeflow.context";

    private static final ThreadLocal<Scope> ACTIVE_RESTORE = new ThreadLocal<>();

    private final ScopeFlow scopeFlow;

    public ScopeFlowThreadLocalAccessor(ScopeFlow scopeFlow) {
        this.scopeFlow = Objects.requireNonNull(scopeFlow, "scopeFlow must not be null");
    }

    @Override
    public Object key() {
        return KEY;
    }

    @Override
    public Map<String, Object> getValue() {
        ScopeContext ctx = scopeFlow.currentContext();
        return ctx.isEmpty() ? null : ctx.asMap();
    }

    @Override
    public void setValue(Map<String, Object> values) {
        // Close any previous restore scope
        Scope prev = ACTIVE_RESTORE.get();
        if (prev != null) {
            prev.close();
        }
        // Open a new scope with the captured values
        Scope scope = scopeFlow.open("micrometer.restore", values);
        ACTIVE_RESTORE.set(scope);
    }

    @Override
    public void setValue() {
        Scope scope = ACTIVE_RESTORE.get();
        if (scope != null) {
            scope.close();
            ACTIVE_RESTORE.remove();
        }
    }
}
