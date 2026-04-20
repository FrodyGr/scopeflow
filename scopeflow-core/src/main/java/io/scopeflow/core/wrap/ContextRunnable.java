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

/**
 * A {@link Runnable} wrapper that captures a {@link ScopeSnapshot} at construction
 * time and restores it before executing the delegate task.
 *
 * <p>This ensures that context is propagated across thread boundaries,
 * especially useful with virtual threads and thread pools.</p>
 *
 * <pre>{@code
 * ScopeSnapshot snapshot = scopeFlow.capture();
 * executor.execute(new ContextRunnable(task, snapshot));
 * }</pre>
 *
 * @since 0.1.0
 */
public final class ContextRunnable implements Runnable {

    private final Runnable delegate;
    private final ScopeSnapshot snapshot;

    /**
     * Creates a new context-propagating runnable.
     *
     * @param delegate the actual task to execute
     * @param snapshot the context snapshot to restore
     * @throws NullPointerException if any argument is null
     */
    public ContextRunnable(Runnable delegate, ScopeSnapshot snapshot) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot must not be null");
    }

    @Override
    public void run() {
        try (Scope scope = snapshot.restore("wrapped.runnable")) {
            delegate.run();
        }
    }

    @Override
    public String toString() {
        return "ContextRunnable{delegate=" + delegate + "}";
    }
}
