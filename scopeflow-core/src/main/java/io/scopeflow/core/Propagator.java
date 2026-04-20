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

/**
 * SPI (Service Provider Interface) for pluggable context propagation mechanisms.
 *
 * <p>A propagator is notified at each stage of the scope lifecycle and is
 * responsible for synchronizing the ScopeFlow context with an external
 * mechanism such as SLF4J MDC, Micrometer, OpenTelemetry, or any custom system.</p>
 *
 * <p>Propagators are registered via {@link ScopeFlowBuilder#propagator(Propagator)}
 * and are invoked in order defined by {@link #order()}.</p>
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>{@link #onScopeOpened} — called immediately after a scope is pushed onto the stack</li>
 *   <li>{@link #onScopeEnriched} — called each time a value is added via {@code Scope.put()}</li>
 *   <li>{@link #onScopeClosing} — called just before the scope is popped (context still active)</li>
 *   <li>{@link #onScopeClosed} — called after the scope is popped (parent context restored)</li>
 * </ol>
 *
 * @since 0.1.0
 * @see ScopeFlowBuilder
 */
public interface Propagator {

    /**
     * Returns the unique name of this propagator (used for logging and diagnostics).
     *
     * @return the propagator name, never {@code null}
     */
    String name();

    /**
     * Called when a new scope has been opened and pushed onto the stack.
     *
     * @param scopeName the name of the scope
     * @param context   the effective context (including inherited parent values)
     */
    void onScopeOpened(String scopeName, ScopeContext context);

    /**
     * Called when a value is added to the active scope via {@code Scope.put()}.
     *
     * @param key     the key being set
     * @param value   the value being set
     * @param context the updated effective context
     */
    void onScopeEnriched(String key, Object value, ScopeContext context);

    /**
     * Called just before a scope is closed, while its context is still active.
     * Useful for cleanup or flushing.
     *
     * @param scopeName the name of the scope being closed
     * @param context   the current context (about to be removed)
     */
    void onScopeClosing(String scopeName, ScopeContext context);

    /**
     * Called after a scope has been closed and the parent context restored.
     *
     * @param scopeName       the name of the closed scope
     * @param restoredContext  the now-active parent context (may be empty)
     */
    void onScopeClosed(String scopeName, ScopeContext restoredContext);

    /**
     * Determines the execution order of this propagator relative to others.
     * Lower values execute first. Default is {@code 0}.
     *
     * @return the order value
     */
    default int order() {
        return 0;
    }
}
