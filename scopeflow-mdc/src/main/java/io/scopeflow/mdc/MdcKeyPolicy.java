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
package io.scopeflow.mdc;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Policy that controls which scope context keys are propagated to SLF4J MDC.
 *
 * <p>By default, only explicitly allowed keys are propagated (deny-by-default).
 * An optional prefix can be added to all MDC keys to avoid collisions with
 * other MDC content.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Only propagate specific keys
 * MdcKeyPolicy policy = MdcKeyPolicy.of(Set.of("request.id", "tenant.id"));
 *
 * // Propagate with a prefix
 * MdcKeyPolicy policy = MdcKeyPolicy.of(Set.of("request.id"), "sf.");
 * // MDC will contain "sf.request.id"
 *
 * // Propagate all keys
 * MdcKeyPolicy policy = MdcKeyPolicy.allowAll();
 * }</pre>
 *
 * @since 0.1.0
 */
public final class MdcKeyPolicy {

    private static final MdcKeyPolicy ALLOW_ALL = new MdcKeyPolicy(Collections.emptySet(), "", true);

    private final Set<String> allowedKeys;
    private final String prefix;
    private final boolean allowAll;

    private MdcKeyPolicy(Set<String> allowedKeys, String prefix, boolean allowAll) {
        this.allowedKeys = Set.copyOf(allowedKeys);
        this.prefix = prefix;
        this.allowAll = allowAll;
    }

    /**
     * Creates a policy that only propagates the specified keys.
     *
     * @param keys the set of context keys to propagate to MDC
     * @return a new policy
     * @throws NullPointerException if {@code keys} is null
     */
    public static MdcKeyPolicy of(Set<String> keys) {
        Objects.requireNonNull(keys, "keys must not be null");
        return new MdcKeyPolicy(keys, "", false);
    }

    /**
     * Creates a policy that only propagates the specified keys,
     * adding a prefix to each MDC key.
     *
     * @param keys   the set of context keys to propagate
     * @param prefix the prefix to add to MDC keys (e.g., {@code "sf."})
     * @return a new policy
     * @throws NullPointerException if any argument is null
     */
    public static MdcKeyPolicy of(Set<String> keys, String prefix) {
        Objects.requireNonNull(keys, "keys must not be null");
        Objects.requireNonNull(prefix, "prefix must not be null");
        return new MdcKeyPolicy(keys, prefix, false);
    }

    /**
     * Creates a policy that propagates all context keys to MDC.
     *
     * @return an allow-all policy
     */
    public static MdcKeyPolicy allowAll() {
        return ALLOW_ALL;
    }

    /**
     * Creates a policy that propagates all context keys with a prefix.
     *
     * @param prefix the prefix to add to MDC keys
     * @return an allow-all-with-prefix policy
     */
    public static MdcKeyPolicy allowAll(String prefix) {
        Objects.requireNonNull(prefix, "prefix must not be null");
        return new MdcKeyPolicy(Collections.emptySet(), prefix, true);
    }

    /**
     * Returns {@code true} if the given context key should be propagated to MDC.
     *
     * @param key the context key to check
     * @return {@code true} if allowed
     */
    public boolean shouldPropagate(String key) {
        return allowAll || allowedKeys.contains(key);
    }

    /**
     * Returns the MDC key name for a given context key, applying the prefix if configured.
     *
     * @param key the context key
     * @return the MDC key (possibly prefixed)
     */
    public String mdcKey(String key) {
        return prefix.isEmpty() ? key : prefix + key;
    }

    /**
     * Returns the set of allowed keys (empty if allow-all mode).
     */
    public Set<String> allowedKeys() {
        return allowedKeys;
    }

    /**
     * Returns the configured prefix.
     */
    public String prefix() {
        return prefix;
    }

    /**
     * Returns true if this policy allows all keys.
     */
    public boolean isAllowAll() {
        return allowAll;
    }

    @Override
    public String toString() {
        if (allowAll) {
            return "MdcKeyPolicy.allowAll(" + (prefix.isEmpty() ? "" : "prefix=" + prefix) + ")";
        }
        return "MdcKeyPolicy{keys=" + allowedKeys + ", prefix='" + prefix + "'}";
    }
}
