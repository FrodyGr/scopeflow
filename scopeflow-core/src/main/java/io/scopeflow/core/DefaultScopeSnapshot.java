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
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * Default implementation of {@link ScopeSnapshot}.
 *
 * <p>Captures a copy of the context values at creation time. Restoring the
 * snapshot opens a new scope with those values on the current thread.</p>
 */
final class DefaultScopeSnapshot implements ScopeSnapshot {

    private final Map<String, Object> capturedValues;
    private final DefaultScopeFlow scopeFlow;

    DefaultScopeSnapshot(Map<String, Object> capturedValues, DefaultScopeFlow scopeFlow) {
        // Defensive copy — the map from asMap() is already unmodifiable,
        // but we keep a reference-safe copy regardless.
        this.capturedValues = Map.copyOf(capturedValues);
        this.scopeFlow = scopeFlow;
    }

    @Override
    public Scope restore(String name) {
        Objects.requireNonNull(name, "name must not be null");
        return scopeFlow.open(name, capturedValues);
    }

    @Override
    public Runnable wrap(Runnable task) {
        Objects.requireNonNull(task, "task must not be null");
        return () -> {
            try (Scope scope = restore("snapshot.runnable")) {
                task.run();
            }
        };
    }

    @Override
    public <T> Callable<T> wrap(Callable<T> task) {
        Objects.requireNonNull(task, "task must not be null");
        return () -> {
            try (Scope scope = restore("snapshot.callable")) {
                return task.call();
            }
        };
    }

    @Override
    public <T> Supplier<T> wrap(Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier must not be null");
        return () -> {
            try (Scope scope = restore("snapshot.supplier")) {
                return supplier.get();
            }
        };
    }

    /**
     * Returns the captured values (for testing).
     */
    Map<String, Object> capturedValues() {
        return capturedValues;
    }

    @Override
    public String toString() {
        return "ScopeSnapshot{values=" + capturedValues + "}";
    }
}
