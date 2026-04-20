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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Default implementation of {@link ScopeContext} backed by a {@link LinkedHashMap}.
 *
 * <p>This class is mutable internally (for scope enrichment) but presents
 * a read-only view through the {@link ScopeContext} interface. Thread safety
 * is guaranteed by the per-thread scope stack design — each context is only
 * ever written to by its owning thread.</p>
 */
final class DefaultScopeContext implements ScopeContext {

    static final ScopeContext EMPTY = new DefaultScopeContext(Collections.emptyMap());

    private final Map<String, Object> values;

    /**
     * Creates a context with the given values (defensive copy).
     */
    DefaultScopeContext(Map<String, ?> values) {
        this.values = new LinkedHashMap<>(values);
    }

    /**
     * Creates a context inheriting all values from a parent context.
     */
    DefaultScopeContext(ScopeContext parent) {
        this.values = new LinkedHashMap<>(parent.asMap());
    }

    /**
     * Internal: adds or updates a value.
     */
    void put(String key, Object value) {
        values.put(key, value);
    }

    /**
     * Internal: adds or updates multiple values.
     */
    void putAll(Map<String, ?> entries) {
        values.putAll(entries);
    }

    @Override
    public Optional<String> get(String key) {
        Object v = values.get(key);
        return v != null ? Optional.of(String.valueOf(v)) : Optional.empty();
    }

    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        Object v = values.get(key);
        if (v != null && type.isInstance(v)) {
            return Optional.of(type.cast(v));
        }
        return Optional.empty();
    }

    @Override
    public Map<String, Object> asMap() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    @Override
    public boolean contains(String key) {
        return values.containsKey(key);
    }

    @Override
    public boolean isEmpty() {
        return values.isEmpty();
    }

    @Override
    public int size() {
        return values.size();
    }

    @Override
    public String toString() {
        return "ScopeContext" + values;
    }
}
