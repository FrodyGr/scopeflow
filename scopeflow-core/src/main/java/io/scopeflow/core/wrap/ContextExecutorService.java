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

import io.scopeflow.core.ScopeFlow;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * An {@link ExecutorService} wrapper that automatically propagates the current
 * scope context to every submitted task.
 *
 * <p>All task submission methods ({@code submit}, {@code invokeAll}, {@code invokeAny})
 * wrap the tasks with context capture/restore. Lifecycle methods ({@code shutdown},
 * {@code awaitTermination}, etc.) are delegated directly.</p>
 *
 * <pre>{@code
 * ExecutorService wrapped = new ContextExecutorService(originalExecutor, scopeFlow);
 * Future<String> result = wrapped.submit(() -> {
 *     // context from the submitting thread is available here
 *     return scopeFlow.currentContext().get("request.id").orElse("N/A");
 * });
 * }</pre>
 *
 * @since 0.1.0
 */
public final class ContextExecutorService implements ExecutorService {

    private final ExecutorService delegate;
    private final ScopeFlow scopeFlow;

    /**
     * Creates a new context-propagating executor service.
     *
     * @param delegate  the underlying executor service to delegate to
     * @param scopeFlow the scope flow instance for context capture
     * @throws NullPointerException if any argument is null
     */
    public ContextExecutorService(ExecutorService delegate, ScopeFlow scopeFlow) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.scopeFlow = Objects.requireNonNull(scopeFlow, "scopeFlow must not be null");
    }

    // -- Task submission (wrapped) --

    @Override
    public void execute(Runnable command) {
        delegate.execute(scopeFlow.wrap(command));
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return delegate.submit(scopeFlow.wrap(task));
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return delegate.submit(scopeFlow.wrap(task), result);
    }

    @Override
    public Future<?> submit(Runnable task) {
        return delegate.submit(scopeFlow.wrap(task));
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
            throws InterruptedException {
        return delegate.invokeAll(wrapAll(tasks));
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks,
                                          long timeout, TimeUnit unit)
            throws InterruptedException {
        return delegate.invokeAll(wrapAll(tasks), timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        return delegate.invokeAny(wrapAll(tasks));
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks,
                            long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return delegate.invokeAny(wrapAll(tasks), timeout, unit);
    }

    // -- Lifecycle (delegated directly) --

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }

    // -- Internal --

    private <T> Collection<Callable<T>> wrapAll(Collection<? extends Callable<T>> tasks) {
        return tasks.stream()
                .map(scopeFlow::wrap)
                .collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return "ContextExecutorService{delegate=" + delegate + "}";
    }
}
