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

/**
 * An auto-closeable contextual scope that holds key-value pairs and propagates
 * them to registered mechanisms (MDC, OpenTelemetry, Micrometer, etc.).
 *
 * <p>A scope is created via {@link ScopeFlow#open(String)} and must be closed
 * when done — ideally via try-with-resources. Closing a scope restores the
 * parent context and notifies all registered propagators.</p>
 *
 * <p>All {@code put} methods return {@code this} for fluent chaining:</p>
 * <pre>{@code
 * try (Scope scope = scopeFlow.open("order.process")
 *         .put("order.id", orderId)
 *         .put("customer.id", customerId)) {
 *     // process order
 * }
 * }</pre>
 *
 * <p>Scopes support nesting: a child scope inherits all values from its parent
 * and can override them. When the child closes, the parent values are restored.</p>
 *
 * @since 0.1.0
 * @see ScopeFlow
 * @see ScopeContext
 */
public interface Scope extends AutoCloseable {

    /**
     * Adds or updates a string value in this scope's context.
     *
     * @param key   the context key
     * @param value the value
     * @return this scope for fluent chaining
     * @throws NullPointerException  if {@code key} is null
     * @throws IllegalStateException if this scope has been closed
     */
    Scope put(String key, String value);

    /**
     * Adds or updates a numeric value in this scope's context.
     *
     * @param key   the context key
     * @param value the numeric value
     * @return this scope for fluent chaining
     * @throws NullPointerException  if {@code key} is null
     * @throws IllegalStateException if this scope has been closed
     */
    Scope put(String key, Number value);

    /**
     * Adds or updates a boolean value in this scope's context.
     *
     * @param key   the context key
     * @param value the boolean value
     * @return this scope for fluent chaining
     * @throws NullPointerException  if {@code key} is null
     * @throws IllegalStateException if this scope has been closed
     */
    Scope put(String key, boolean value);

    /**
     * Adds or updates multiple values at once.
     *
     * @param values the key-value pairs to add
     * @return this scope for fluent chaining
     * @throws NullPointerException  if {@code values} is null
     * @throws IllegalStateException if this scope has been closed
     */
    Scope putAll(Map<String, ?> values);

    /**
     * Returns the name given to this scope when it was opened.
     *
     * @return the scope name, never {@code null}
     */
    String name();

    /**
     * Returns a read-only view of the current context within this scope,
     * including inherited parent values.
     *
     * @return the effective {@link ScopeContext}
     */
    ScopeContext context();

    /**
     * Closes this scope, restoring the parent context and notifying propagators.
     *
     * <p>This method is idempotent: calling it multiple times has no additional
     * effect after the first call. It never throws exceptions.</p>
     */
    @Override
    void close();
}
