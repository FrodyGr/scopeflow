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
package io.scopeflow.core.wrap;

import io.scopeflow.core.ScopeFlow;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * An {@link Executor} wrapper that automatically propagates the current
 * scope context to every submitted task.
 *
 * <pre>{@code
 * Executor wrapped = new ContextExecutor(originalExecutor, scopeFlow);
 * wrapped.execute(() -> {
 *     // context from the submitting thread is available here
 * });
 * }</pre>
 *
 * @since 0.1.0
 */
public final class ContextExecutor implements Executor {

    private final Executor delegate;
    private final ScopeFlow scopeFlow;

    /**
     * Creates a new context-propagating executor.
     *
     * @param delegate  the underlying executor to delegate to
     * @param scopeFlow the scope flow instance for context capture
     * @throws NullPointerException if any argument is null
     */
    public ContextExecutor(Executor delegate, ScopeFlow scopeFlow) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.scopeFlow = Objects.requireNonNull(scopeFlow, "scopeFlow must not be null");
    }

    @Override
    public void execute(Runnable command) {
        delegate.execute(scopeFlow.wrap(command));
    }

    @Override
    public String toString() {
        return "ContextExecutor{delegate=" + delegate + "}";
    }
}
