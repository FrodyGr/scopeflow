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

import io.scopeflow.core.Propagator;
import io.scopeflow.core.ScopeContext;
import org.slf4j.MDC;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A {@link Propagator} that bridges ScopeFlow context to SLF4J {@link MDC}.
 *
 * <p>When a scope is opened, this propagator copies allowed context keys
 * into the MDC. When the scope is closed, the previous MDC values are restored.
 * This ensures consistent correlated logging within and across scopes.</p>
 *
 * <p>A per-thread stack of saved MDC states ensures correct behavior for
 * nested scopes: each level saves/restores independently.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * MdcKeyPolicy policy = MdcKeyPolicy.of(Set.of("request.id", "tenant.id"));
 * MdcPropagator propagator = new MdcPropagator(policy);
 *
 * ScopeFlow scopeFlow = ScopeFlowBuilder.create()
 *     .propagator(propagator)
 *     .build();
 *
 * try (Scope scope = scopeFlow.open("http.request")
 *         .put("request.id", "abc-123")
 *         .put("tenant.id", "acme")) {
 *     log.info("This log will have request.id=abc-123 and tenant.id=acme in MDC");
 * }
 * // MDC is clean here
 * }</pre>
 *
 * @since 0.1.0
 * @see MdcKeyPolicy
 */
public final class MdcPropagator implements Propagator {

    private static final String PROPAGATOR_NAME = "mdc";

    /**
     * Per-thread stack of saved MDC values for restoration when scopes close.
     * Each entry maps MDC key → previous value (null means "was not set").
     */
    private static final ThreadLocal<ArrayDeque<Map<String, String>>> MDC_STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    private final MdcKeyPolicy policy;

    /**
     * Creates a new MDC propagator with the given key policy.
     *
     * @param policy the policy controlling which keys propagate to MDC
     * @throws NullPointerException if {@code policy} is null
     */
    public MdcPropagator(MdcKeyPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
    }

    @Override
    public String name() {
        return PROPAGATOR_NAME;
    }

    @Override
    public void onScopeOpened(String scopeName, ScopeContext context) {
        Map<String, String> savedValues = new HashMap<>();

        // For each entry in the context, if the policy allows it,
        // save the current MDC value and set the new one.
        for (Map.Entry<String, Object> entry : context.asMap().entrySet()) {
            String contextKey = entry.getKey();
            if (policy.shouldPropagate(contextKey)) {
                String mdcKey = policy.mdcKey(contextKey);
                // Save current MDC value (may be null if not set)
                savedValues.put(mdcKey, MDC.get(mdcKey));
                // Set new value
                MDC.put(mdcKey, String.valueOf(entry.getValue()));
            }
        }

        MDC_STACK.get().push(savedValues);
    }

    @Override
    public void onScopeEnriched(String key, Object value, ScopeContext context) {
        if (policy.shouldPropagate(key)) {
            String mdcKey = policy.mdcKey(key);

            // If this key was not saved during onScopeOpened (e.g., it was added
            // via put() after the scope was opened), save the previous MDC value
            // so it can be restored when the scope closes.
            ArrayDeque<Map<String, String>> stack = MDC_STACK.get();
            if (!stack.isEmpty()) {
                Map<String, String> savedValues = stack.peek();
                if (!savedValues.containsKey(mdcKey)) {
                    savedValues.put(mdcKey, MDC.get(mdcKey));
                }
            }

            MDC.put(mdcKey, String.valueOf(value));
        }
    }

    @Override
    public void onScopeClosing(String scopeName, ScopeContext context) {
        // no-op: cleanup happens in onScopeClosed
    }

    @Override
    public void onScopeClosed(String scopeName, ScopeContext restoredContext) {
        ArrayDeque<Map<String, String>> stack = MDC_STACK.get();
        if (stack.isEmpty()) {
            return;
        }

        Map<String, String> savedValues = stack.pop();

        // Restore previous MDC values
        for (Map.Entry<String, String> entry : savedValues.entrySet()) {
            String mdcKey = entry.getKey();
            String previousValue = entry.getValue();
            if (previousValue == null) {
                MDC.remove(mdcKey);
            } else {
                MDC.put(mdcKey, previousValue);
            }
        }

        // Clean up ThreadLocal if stack is empty
        if (stack.isEmpty()) {
            MDC_STACK.remove();
        }
    }

    @Override
    public int order() {
        // MDC propagator should run early so that logs within onScopeOpened
        // of other propagators already have MDC set
        return -100;
    }

    /**
     * Returns the key policy used by this propagator.
     */
    public MdcKeyPolicy policy() {
        return policy;
    }

    @Override
    public String toString() {
        return "MdcPropagator{policy=" + policy + "}";
    }
}
