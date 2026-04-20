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
import java.util.Map;
import java.util.Optional;

/**
 * A read-only view of the key-value pairs currently active in a scope.
 *
 * <p>The context includes all values from the current scope as well as
 * inherited values from parent scopes. It provides type-safe access
 * to values and can be exported as an unmodifiable map.</p>
 *
 * @since 0.1.0
 * @see Scope
 */
public interface ScopeContext {

    /**
     * Retrieves a value as a string.
     *
     * @param key the context key
     * @return the value converted to string, or empty if not present
     */
    Optional<String> get(String key);

    /**
     * Retrieves a value with a specific type.
     *
     * @param <T>  the expected type
     * @param key  the context key
     * @param type the expected class
     * @return the value if present and assignable to the type, or empty otherwise
     */
    <T> Optional<T> get(String key, Class<T> type);

    /**
     * Returns all context entries as an unmodifiable map.
     * Changes to the underlying scope do not affect the returned map.
     *
     * @return an unmodifiable snapshot of the context entries
     */
    Map<String, Object> asMap();

    /**
     * Checks whether the context contains a given key.
     *
     * @param key the context key
     * @return {@code true} if the key is present
     */
    boolean contains(String key);

    /**
     * Returns {@code true} if this context has no entries.
     *
     * @return {@code true} if empty
     */
    boolean isEmpty();

    /**
     * Returns the number of entries in this context.
     *
     * @return the entry count
     */
    int size();

    /**
     * Returns an empty context with no entries.
     *
     * @return an empty {@code ScopeContext}
     */
    static ScopeContext empty() {
        return DefaultScopeContext.EMPTY;
    }

    /**
     * Creates a read-only context from the given map.
     *
     * @param values the key-value pairs
     * @return a new {@code ScopeContext} wrapping the values
     */
    static ScopeContext of(Map<String, ?> values) {
        if (values == null || values.isEmpty()) {
            return empty();
        }
        return new DefaultScopeContext(values);
    }
}
