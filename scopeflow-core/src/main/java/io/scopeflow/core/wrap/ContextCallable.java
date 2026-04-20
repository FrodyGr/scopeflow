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

import io.scopeflow.core.Scope;
import io.scopeflow.core.ScopeSnapshot;

import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * A {@link Callable} wrapper that restores a captured {@link ScopeSnapshot}
 * before executing the delegate callable.
 *
 * @param <T> the return type of the callable
 * @since 0.1.0
 */
public final class ContextCallable<T> implements Callable<T> {

    private final Callable<T> delegate;
    private final ScopeSnapshot snapshot;

    /**
     * Creates a new context-propagating callable.
     *
     * @param delegate the actual callable to execute
     * @param snapshot the context snapshot to restore
     * @throws NullPointerException if any argument is null
     */
    public ContextCallable(Callable<T> delegate, ScopeSnapshot snapshot) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot must not be null");
    }

    @Override
    public T call() throws Exception {
        try (Scope scope = snapshot.restore("wrapped.callable")) {
            return delegate.call();
        }
    }

    @Override
    public String toString() {
        return "ContextCallable{delegate=" + delegate + "}";
    }
}
