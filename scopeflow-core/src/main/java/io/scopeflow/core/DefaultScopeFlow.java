/*
 * Copyright 2026 ScopeFlow Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.scopeflow.core;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * Default thread-local-based implementation of {@link ScopeFlow}.
 *
 * <p>Maintains a per-thread stack of scope entries using {@link ThreadLocal}.
 * Each virtual or platform thread gets its own independent stack, ensuring
 * full context isolation without synchronization.</p>
 *
 * <p>Use {@link ScopeFlowBuilder} to create instances with custom propagators
 * and key policies.</p>
 *
 * @since 0.1.0
 */
public final class DefaultScopeFlow implements ScopeFlow {

    /**
     * Per-thread stack of scope entries. Each entry holds the scope name,
     * its context, and a reference to the parent context for restoration.
     */
    private static final ThreadLocal<ArrayDeque<ScopeEntry>> SCOPE_STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    private final PropagatorRegistry propagators;
    private final ContextKeyPolicy keyPolicy;

    /**
     * Internal record representing a pushed scope on the stack.
     */
    record ScopeEntry(String name, DefaultScopeContext context, ScopeContext parentContext) {
    }

    DefaultScopeFlow(PropagatorRegistry propagators, ContextKeyPolicy keyPolicy) {
        this.propagators = propagators;
        this.keyPolicy = keyPolicy;
    }

    @Override
    public Scope open(String name) {
        return open(name, Collections.emptyMap());
    }

    @Override
    public Scope open(String name, Map<String, ?> initialValues) {
        Objects.requireNonNull(name, "scope name must not be null");
        Objects.requireNonNull(initialValues, "initialValues must not be null");

        ArrayDeque<ScopeEntry> stack = SCOPE_STACK.get();
        ScopeContext parentContext = stack.isEmpty()
                ? ScopeContext.empty()
                : stack.peek().context();

        // Create child context inheriting parent values
        DefaultScopeContext newContext = new DefaultScopeContext(parentContext);

        // Apply initial values, respecting key policy
        initialValues.forEach((key, value) -> {
            if (keyPolicy.isAllowed(key)) {
                newContext.put(key, value);
            }
        });

        // Push onto the stack
        ScopeEntry entry = new ScopeEntry(name, newContext, parentContext);
        stack.push(entry);

        // Notify propagators
        propagators.notifyScopeOpened(name, newContext);

        return new DefaultScope(name, newContext, this);
    }

    @Override
    public ScopeSnapshot capture() {
        return new DefaultScopeSnapshot(currentContext().asMap(), this);
    }

    @Override
    public ScopeContext currentContext() {
        ArrayDeque<ScopeEntry> stack = SCOPE_STACK.get();
        return stack.isEmpty() ? ScopeContext.empty() : stack.peek().context();
    }

    // -- Task wrapping --

    @Override
    public Runnable wrap(Runnable task) {
        Objects.requireNonNull(task, "task must not be null");
        ScopeSnapshot snapshot = capture();
        return snapshot.wrap(task);
    }

    @Override
    public <T> Callable<T> wrap(Callable<T> task) {
        Objects.requireNonNull(task, "task must not be null");
        ScopeSnapshot snapshot = capture();
        return snapshot.wrap(task);
    }

    @Override
    public <T> Supplier<T> wrap(Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier must not be null");
        ScopeSnapshot snapshot = capture();
        return snapshot.wrap(supplier);
    }

    // -- Convenience execution --

    @Override
    public void run(String name, Runnable task) {
        run(name, Collections.emptyMap(), task);
    }

    @Override
    public void run(String name, Map<String, ?> values, Runnable task) {
        Objects.requireNonNull(task, "task must not be null");
        try (Scope scope = open(name, values)) {
            task.run();
        }
    }

    @Override
    public <T> T call(String name, Callable<T> task) throws Exception {
        return call(name, Collections.emptyMap(), task);
    }

    @Override
    public <T> T call(String name, Map<String, ?> values, Callable<T> task) throws Exception {
        Objects.requireNonNull(task, "task must not be null");
        try (Scope scope = open(name, values)) {
            return task.call();
        }
    }

    // -- Internal lifecycle management --

    /**
     * Called by {@link DefaultScope#close()} to pop the scope from the stack
     * and notify propagators.
     */
    void closeScope(DefaultScope scope) {
        ArrayDeque<ScopeEntry> stack = SCOPE_STACK.get();

        if (stack.isEmpty()) {
            return;
        }

        // Notify propagators: scope is about to close (context still accessible)
        ScopeEntry current = stack.peek();
        propagators.notifyScopeClosing(current.name(), current.context());

        // Pop the scope
        stack.pop();

        // Determine the restored context
        ScopeContext restoredContext = stack.isEmpty()
                ? ScopeContext.empty()
                : stack.peek().context();

        // Notify propagators: scope is closed, parent context restored
        propagators.notifyScopeClosed(scope.name(), restoredContext);

        // Clean up ThreadLocal if stack is empty to prevent memory leaks
        if (stack.isEmpty()) {
            SCOPE_STACK.remove();
        }
    }

    /**
     * Called by {@link DefaultScope#put} to notify propagators of enrichment.
     */
    void notifyEnriched(String key, Object value, ScopeContext context) {
        propagators.notifyScopeEnriched(key, value, context);
    }

    /**
     * Returns the key policy used by this instance.
     */
    ContextKeyPolicy keyPolicy() {
        return keyPolicy;
    }

    /**
     * Returns the propagator registry (for testing and diagnostics).
     */
    PropagatorRegistry propagatorRegistry() {
        return propagators;
    }
}
