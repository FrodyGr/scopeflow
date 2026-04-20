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

import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * A capturable/restorable snapshot of the current scope context.
 *
 * <p>Snapshots allow context to be captured on one thread and restored
 * on another, enabling consistent propagation across asynchronous
 * boundaries such as thread pools, virtual threads, and executors.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // On the originating thread
 * ScopeSnapshot snapshot = scopeFlow.capture();
 *
 * // On the target thread
 * try (Scope scope = snapshot.restore("async.task")) {
 *     // context is now active here
 * }
 *
 * // Or wrap a task directly
 * executor.submit(snapshot.wrap(() -> service.process()));
 * }</pre>
 *
 * @since 0.1.0
 * @see ScopeFlow#capture()
 */
public interface ScopeSnapshot {

    /**
     * Restores this snapshot as a new scope in the current thread.
     * The caller is responsible for closing the returned scope.
     *
     * @param name a descriptive name for the restored scope
     * @return a new {@link Scope} with this snapshot's context values
     */
    Scope restore(String name);

    /**
     * Wraps a {@link Runnable} so that this snapshot's context is restored
     * around its execution.
     *
     * @param task the task to wrap
     * @return a context-restoring wrapper
     * @throws NullPointerException if {@code task} is null
     */
    Runnable wrap(Runnable task);

    /**
     * Wraps a {@link Callable} so that this snapshot's context is restored
     * around its execution.
     *
     * @param <T>  the return type
     * @param task the callable to wrap
     * @return a context-restoring wrapper
     * @throws NullPointerException if {@code task} is null
     */
    <T> Callable<T> wrap(Callable<T> task);

    /**
     * Wraps a {@link Supplier} so that this snapshot's context is restored
     * around its execution.
     *
     * @param <T>      the return type
     * @param supplier the supplier to wrap
     * @return a context-restoring wrapper
     * @throws NullPointerException if {@code supplier} is null
     */
    <T> Supplier<T> wrap(Supplier<T> supplier);
}
