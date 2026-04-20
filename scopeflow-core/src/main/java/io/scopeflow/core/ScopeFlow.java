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

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * Main facade for the ScopeFlow context propagation library.
 *
 * <p>{@code ScopeFlow} is the primary entry point for creating scopes, capturing
 * context snapshots, and wrapping tasks for cross-thread propagation. It is
 * designed to be used as an injectable singleton (e.g., a Spring bean).</p>
 *
 * <h2>Basic usage</h2>
 * <pre>{@code
 * try (Scope scope = scopeFlow.open("http.request")) {
 *     scope.put("request.id", requestId);
 *     scope.put("tenant.id", tenantId);
 *     // context is active here, propagated to MDC, OTel, etc.
 * }
 * // context is automatically cleaned up
 * }</pre>
 *
 * <h2>Async propagation</h2>
 * <pre>{@code
 * Runnable wrapped = scopeFlow.wrap(() -> service.process());
 * executor.submit(wrapped); // context propagated to the new thread
 * }</pre>
 *
 * @since 0.1.0
 * @see Scope
 * @see ScopeSnapshot
 * @see ScopeFlowBuilder
 */
public interface ScopeFlow {

    /**
     * Opens a new named scope with an empty initial context.
     * The scope inherits all values from the current parent scope, if any.
     *
     * @param name a descriptive name for the scope (e.g., {@code "http.request"},
     *             {@code "order.process"})
     * @return a new {@link Scope} that must be closed when finished
     * @throws NullPointerException if {@code name} is null
     */
    Scope open(String name);

    /**
     * Opens a new named scope and populates it with initial values.
     * The scope inherits all values from the current parent scope, if any,
     * and applies the given initial values on top.
     *
     * @param name          a descriptive name for the scope
     * @param initialValues key-value pairs to populate the scope with
     * @return a new {@link Scope} that must be closed when finished
     * @throws NullPointerException if {@code name} or {@code initialValues} is null
     */
    Scope open(String name, Map<String, ?> initialValues);

    /**
     * Captures a snapshot of the current context that can be restored
     * later, typically in a different thread.
     *
     * @return a {@link ScopeSnapshot} representing the current context,
     *         or an empty snapshot if no scope is active
     */
    ScopeSnapshot capture();

    /**
     * Returns the current active context, or an empty context if no scope is active.
     *
     * @return the current {@link ScopeContext}, never {@code null}
     */
    ScopeContext currentContext();

    // -- Task wrapping --

    /**
     * Wraps a {@link Runnable} so that the current context is propagated
     * when it executes (potentially in a different thread).
     *
     * @param task the task to wrap
     * @return a context-propagating wrapper around the task
     * @throws NullPointerException if {@code task} is null
     */
    Runnable wrap(Runnable task);

    /**
     * Wraps a {@link Callable} so that the current context is propagated
     * when it executes.
     *
     * @param <T>  the return type of the callable
     * @param task the task to wrap
     * @return a context-propagating wrapper around the task
     * @throws NullPointerException if {@code task} is null
     */
    <T> Callable<T> wrap(Callable<T> task);

    /**
     * Wraps a {@link Supplier} so that the current context is propagated
     * when it executes.
     *
     * @param <T>      the return type of the supplier
     * @param supplier the supplier to wrap
     * @return a context-propagating wrapper around the supplier
     * @throws NullPointerException if {@code supplier} is null
     */
    <T> Supplier<T> wrap(Supplier<T> supplier);

    // -- Convenience execution --

    /**
     * Opens a scope, runs the task within it, and closes the scope automatically.
     *
     * @param name the scope name
     * @param task the task to run
     * @throws NullPointerException if {@code name} or {@code task} is null
     */
    void run(String name, Runnable task);

    /**
     * Opens a scope with initial values, runs the task, and closes the scope.
     *
     * @param name   the scope name
     * @param values initial context values
     * @param task   the task to run
     * @throws NullPointerException if any argument is null
     */
    void run(String name, Map<String, ?> values, Runnable task);

    /**
     * Opens a scope, calls the task within it, and returns the result.
     *
     * @param <T>  the return type
     * @param name the scope name
     * @param task the callable to execute
     * @return the result of the callable
     * @throws Exception            if the callable throws
     * @throws NullPointerException if {@code name} or {@code task} is null
     */
    <T> T call(String name, Callable<T> task) throws Exception;

    /**
     * Opens a scope with initial values, calls the task, and returns the result.
     *
     * @param <T>    the return type
     * @param name   the scope name
     * @param values initial context values
     * @param task   the callable to execute
     * @return the result of the callable
     * @throws Exception            if the callable throws
     * @throws NullPointerException if any argument is null
     */
    <T> T call(String name, Map<String, ?> values, Callable<T> task) throws Exception;
}
