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
import java.util.Objects;
import java.util.Set;

/**
 * Policy that controls which context keys are allowed to be stored,
 * and optionally sanitizes their values before propagation.
 *
 * <p>This is used by the core to filter keys at write time, and by propagators
 * to decide which keys should be exported to external mechanisms.</p>
 *
 * @since 0.1.0
 */
public interface ContextKeyPolicy {

    /**
     * Returns {@code true} if the given key is allowed by this policy.
     *
     * @param key the context key to check
     * @return {@code true} if allowed
     */
    boolean isAllowed(String key);

    /**
     * Sanitizes a value before it is stored or propagated.
     * The default implementation returns the value's string representation unchanged.
     *
     * @param key   the context key
     * @param value the raw value
     * @return the sanitized string representation
     */
    default String sanitize(String key, Object value) {
        return value != null ? value.toString() : "";
    }

    /**
     * Returns a policy that allows all keys.
     *
     * @return an allow-all policy
     */
    static ContextKeyPolicy allowAll() {
        return AllowAllPolicy.INSTANCE;
    }

    /**
     * Returns a policy that allows only the specified keys.
     *
     * @param keys the set of allowed keys
     * @return an allowlist policy
     * @throws NullPointerException if {@code keys} is null
     */
    static ContextKeyPolicy allowList(Set<String> keys) {
        Objects.requireNonNull(keys, "keys must not be null");
        Set<String> immutableKeys = Set.copyOf(keys);
        return new ContextKeyPolicy() {
            @Override
            public boolean isAllowed(String key) {
                return immutableKeys.contains(key);
            }

            @Override
            public String toString() {
                return "ContextKeyPolicy.allowList(" + immutableKeys + ")";
            }
        };
    }

    /**
     * Returns a policy that allows all keys except the specified ones.
     *
     * @param keys the set of denied keys
     * @return a denylist policy
     * @throws NullPointerException if {@code keys} is null
     */
    static ContextKeyPolicy denyList(Set<String> keys) {
        Objects.requireNonNull(keys, "keys must not be null");
        Set<String> immutableKeys = Set.copyOf(keys);
        return new ContextKeyPolicy() {
            @Override
            public boolean isAllowed(String key) {
                return !immutableKeys.contains(key);
            }

            @Override
            public String toString() {
                return "ContextKeyPolicy.denyList(" + immutableKeys + ")";
            }
        };
    }
}

/**
 * Package-private singleton for the allow-all policy.
 */
enum AllowAllPolicy implements ContextKeyPolicy {
    INSTANCE;

    @Override
    public boolean isAllowed(String key) {
        return true;
    }

    @Override
    public String toString() {
        return "ContextKeyPolicy.allowAll()";
    }
}
