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
import java.util.Comparator;
import java.util.List;

/**
 * Internal registry that manages the ordered list of {@link Propagator} instances
 * and dispatches lifecycle notifications to them.
 *
 * <p>Propagators are sorted by {@link Propagator#order()} at construction time
 * and invoked in that order for all lifecycle events. Exceptions thrown by
 * individual propagators are caught and logged to prevent one propagator from
 * breaking the scope lifecycle.</p>
 */
final class PropagatorRegistry {

    private static final System.Logger LOG = System.getLogger(PropagatorRegistry.class.getName());

    static final PropagatorRegistry EMPTY = new PropagatorRegistry(List.of());

    private final List<Propagator> propagators;

    PropagatorRegistry(List<Propagator> propagators) {
        List<Propagator> sorted = new ArrayList<>(propagators);
        sorted.sort(Comparator.comparingInt(Propagator::order));
        this.propagators = List.copyOf(sorted);
    }

    void notifyScopeOpened(String name, ScopeContext context) {
        for (Propagator p : propagators) {
            try {
                p.onScopeOpened(name, context);
            } catch (Exception e) {
                LOG.log(System.Logger.Level.WARNING,
                        "Propagator ''{0}'' threw on onScopeOpened: {1}",
                        p.name(), e.getMessage());
            }
        }
    }

    void notifyScopeEnriched(String key, Object value, ScopeContext context) {
        for (Propagator p : propagators) {
            try {
                p.onScopeEnriched(key, value, context);
            } catch (Exception e) {
                LOG.log(System.Logger.Level.WARNING,
                        "Propagator ''{0}'' threw on onScopeEnriched: {1}",
                        p.name(), e.getMessage());
            }
        }
    }

    void notifyScopeClosing(String name, ScopeContext context) {
        for (Propagator p : propagators) {
            try {
                p.onScopeClosing(name, context);
            } catch (Exception e) {
                LOG.log(System.Logger.Level.WARNING,
                        "Propagator ''{0}'' threw on onScopeClosing: {1}",
                        p.name(), e.getMessage());
            }
        }
    }

    void notifyScopeClosed(String name, ScopeContext restoredContext) {
        for (Propagator p : propagators) {
            try {
                p.onScopeClosed(name, restoredContext);
            } catch (Exception e) {
                LOG.log(System.Logger.Level.WARNING,
                        "Propagator ''{0}'' threw on onScopeClosed: {1}",
                        p.name(), e.getMessage());
            }
        }
    }

    /**
     * Returns the number of registered propagators.
     */
    int size() {
        return propagators.size();
    }

    /**
     * Returns an unmodifiable view of the propagators.
     */
    List<Propagator> propagators() {
        return propagators;
    }
}
