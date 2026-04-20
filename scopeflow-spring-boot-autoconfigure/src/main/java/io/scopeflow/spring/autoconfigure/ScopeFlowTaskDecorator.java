/*
 * Copyright 2026 ScopeFlow Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.scopeflow.spring.autoconfigure;

import io.scopeflow.core.ScopeFlow;
import org.springframework.core.task.TaskDecorator;

/**
 * A Spring {@link TaskDecorator} that wraps tasks with ScopeFlow context propagation.
 *
 * <p>This decorator captures the current scope context when the task is submitted
 * and restores it when the task executes, ensuring that {@code @Async} methods
 * and tasks submitted to Spring-managed executors retain their originating context.</p>
 *
 * @since 0.2.0
 */
public class ScopeFlowTaskDecorator implements TaskDecorator {

    private final ScopeFlow scopeFlow;

    public ScopeFlowTaskDecorator(ScopeFlow scopeFlow) {
        this.scopeFlow = scopeFlow;
    }

    @Override
    public Runnable decorate(Runnable runnable) {
        return scopeFlow.wrap(runnable);
    }
}
