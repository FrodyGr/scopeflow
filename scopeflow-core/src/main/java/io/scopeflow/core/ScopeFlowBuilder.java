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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Fluent builder for creating {@link ScopeFlow} instances.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * ScopeFlow scopeFlow = ScopeFlowBuilder.create()
 *     .propagator(new MdcPropagator(allowedKeys))
 *     .keyPolicy(ContextKeyPolicy.allowAll())
 *     .build();
 * }</pre>
 *
 * @since 0.1.0
 * @see ScopeFlow
 * @see Propagator
 * @see ContextKeyPolicy
 */
public final class ScopeFlowBuilder {

    private final List<Propagator> propagators = new ArrayList<>();
    private ContextKeyPolicy keyPolicy = ContextKeyPolicy.allowAll();

    private ScopeFlowBuilder() {
    }

    /**
     * Creates a new builder with default settings.
     *
     * @return a new builder
     */
    public static ScopeFlowBuilder create() {
        return new ScopeFlowBuilder();
    }

    /**
     * Registers a propagator to be notified on scope lifecycle events.
     *
     * @param propagator the propagator to register
     * @return this builder
     * @throws NullPointerException if {@code propagator} is null
     */
    public ScopeFlowBuilder propagator(Propagator propagator) {
        Objects.requireNonNull(propagator, "propagator must not be null");
        this.propagators.add(propagator);
        return this;
    }

    /**
     * Registers multiple propagators at once.
     *
     * @param propagators the propagators to register
     * @return this builder
     * @throws NullPointerException if {@code propagators} is null
     */
    public ScopeFlowBuilder propagators(List<Propagator> propagators) {
        Objects.requireNonNull(propagators, "propagators must not be null");
        this.propagators.addAll(propagators);
        return this;
    }

    /**
     * Sets the key policy that controls which keys can be added to scopes.
     *
     * @param keyPolicy the policy to use
     * @return this builder
     * @throws NullPointerException if {@code keyPolicy} is null
     */
    public ScopeFlowBuilder keyPolicy(ContextKeyPolicy keyPolicy) {
        Objects.requireNonNull(keyPolicy, "keyPolicy must not be null");
        this.keyPolicy = keyPolicy;
        return this;
    }

    /**
     * Builds a new {@link ScopeFlow} instance with the configured settings.
     *
     * @return a new {@link ScopeFlow}
     */
    public ScopeFlow build() {
        PropagatorRegistry registry = propagators.isEmpty()
                ? PropagatorRegistry.EMPTY
                : new PropagatorRegistry(propagators);
        return new DefaultScopeFlow(registry, keyPolicy);
    }
}
