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
import java.util.Objects;

/**
 * Default implementation of {@link Scope}.
 *
 * <p>A scope maintains a mutable context that can be enriched via {@code put()}
 * methods. On creation it inherits values from the parent context. On close,
 * the parent context is restored and propagators are notified.</p>
 *
 * <p>Close is idempotent: calling it multiple times has no additional effect.</p>
 */
final class DefaultScope implements Scope {

    private final String name;
    private final DefaultScopeContext context;
    private final DefaultScopeFlow scopeFlow;
    private volatile boolean closed;

    DefaultScope(String name, DefaultScopeContext context, DefaultScopeFlow scopeFlow) {
        this.name = name;
        this.context = context;
        this.scopeFlow = scopeFlow;
        this.closed = false;
    }

    @Override
    public Scope put(String key, String value) {
        Objects.requireNonNull(key, "key must not be null");
        checkNotClosed();
        context.put(key, value);
        scopeFlow.notifyEnriched(key, value, context);
        return this;
    }

    @Override
    public Scope put(String key, Number value) {
        Objects.requireNonNull(key, "key must not be null");
        checkNotClosed();
        context.put(key, value);
        scopeFlow.notifyEnriched(key, value, context);
        return this;
    }

    @Override
    public Scope put(String key, boolean value) {
        Objects.requireNonNull(key, "key must not be null");
        checkNotClosed();
        context.put(key, value);
        scopeFlow.notifyEnriched(key, value, context);
        return this;
    }

    @Override
    public Scope putAll(Map<String, ?> values) {
        Objects.requireNonNull(values, "values must not be null");
        checkNotClosed();
        values.forEach((key, value) -> {
            Objects.requireNonNull(key, "key must not be null");
            context.put(key, value);
            scopeFlow.notifyEnriched(key, value, context);
        });
        return this;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public ScopeContext context() {
        return context;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            scopeFlow.closeScope(this);
        }
    }

    private void checkNotClosed() {
        if (closed) {
            throw new IllegalStateException("Scope '" + name + "' has already been closed");
        }
    }

    @Override
    public String toString() {
        return "Scope{name='" + name + "', closed=" + closed + ", context=" + context + "}";
    }
}
