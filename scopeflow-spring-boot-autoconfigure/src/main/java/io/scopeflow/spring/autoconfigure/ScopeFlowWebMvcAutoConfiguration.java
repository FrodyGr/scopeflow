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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Auto-configuration for ScopeFlow Spring Web MVC and task execution integration.
 *
 * <p>When Spring MVC is on the classpath, this registers a {@link ScopeFlowMvcInterceptor}
 * that opens a scope per HTTP request. It also configures a {@link ScopeFlowTaskDecorator}
 * for automatic context propagation in {@code @Async} tasks and executor-based work.</p>
 *
 * @since 0.2.0
 */
@AutoConfiguration(after = ScopeFlowAutoConfiguration.class)
@ConditionalOnBean(ScopeFlow.class)
@ConditionalOnProperty(prefix = "scopeflow", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ScopeFlowWebMvcAutoConfiguration {

    // ---- MVC Interceptor ----

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(HandlerInterceptor.class)
    @ConditionalOnProperty(prefix = "scopeflow.http.server", name = "generate-request-scope",
            havingValue = "true", matchIfMissing = true)
    static class MvcInterceptorConfiguration implements WebMvcConfigurer {

        private final ObjectProvider<ScopeFlowMvcInterceptor> interceptor;

        MvcInterceptorConfiguration(ObjectProvider<ScopeFlowMvcInterceptor> interceptor) {
            this.interceptor = interceptor;
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            interceptor.ifAvailable(i -> registry.addInterceptor(i).addPathPatterns("/**"));
        }

        @Bean
        @ConditionalOnMissingBean
        public ScopeFlowMvcInterceptor scopeFlowMvcInterceptor(ScopeFlow scopeFlow,
                                                                ScopeFlowProperties props) {
            return new ScopeFlowMvcInterceptor(scopeFlow, props.getHttp().getServer());
        }
    }

    // ---- Task Decorator ----

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(TaskDecorator.class)
    static class TaskDecoratorConfiguration {

        @Bean
        @ConditionalOnMissingBean(ScopeFlowTaskDecorator.class)
        public ScopeFlowTaskDecorator scopeFlowTaskDecorator(ScopeFlow scopeFlow) {
            return new ScopeFlowTaskDecorator(scopeFlow);
        }

        /**
         * Applies the task decorator to Spring-managed task executors after all beans
         * have been fully initialized, supporting both {@link ThreadPoolTaskExecutor}
         * and {@link SimpleAsyncTaskExecutor} (used when virtual threads are enabled).
         */
        @Bean
        public org.springframework.beans.factory.SmartInitializingSingleton scopeFlowTaskExecutorConfigurer(
                org.springframework.beans.factory.ListableBeanFactory beanFactory,
                ScopeFlowTaskDecorator taskDecorator) {
            return () -> {
                beanFactory.getBeansOfType(ThreadPoolTaskExecutor.class)
                        .values()
                        .forEach(executor -> executor.setTaskDecorator(taskDecorator));

                beanFactory.getBeansOfType(SimpleAsyncTaskExecutor.class)
                        .values()
                        .forEach(executor -> executor.setTaskDecorator(taskDecorator));
            };
        }
    }
}
