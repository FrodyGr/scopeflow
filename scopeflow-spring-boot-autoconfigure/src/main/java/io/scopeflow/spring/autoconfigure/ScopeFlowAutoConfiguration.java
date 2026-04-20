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

import io.scopeflow.core.Propagator;
import io.scopeflow.core.ScopeFlow;
import io.scopeflow.core.ScopeFlowBuilder;
import io.scopeflow.mdc.MdcKeyPolicy;
import io.scopeflow.mdc.MdcPropagator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/**
 * Main auto-configuration for ScopeFlow.
 *
 * <p>Creates a {@link ScopeFlow} bean with all available {@link Propagator} beans
 * and optionally configures MDC propagation when {@code scopeflow-mdc} is on
 * the classpath.</p>
 *
 * @since 0.2.0
 */
@AutoConfiguration
@EnableConfigurationProperties(ScopeFlowProperties.class)
@ConditionalOnProperty(prefix = "scopeflow", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ScopeFlowAutoConfiguration {

    /**
     * Creates the main {@link ScopeFlow} bean, wiring all registered {@link Propagator} beans.
     */
    @Bean
    @ConditionalOnMissingBean
    public ScopeFlow scopeFlow(ObjectProvider<Propagator> propagators) {
        ScopeFlowBuilder builder = ScopeFlowBuilder.create();
        propagators.orderedStream().forEach(builder::propagator);
        return builder.build();
    }

    /**
     * MDC auto-configuration — only active when {@code scopeflow-mdc} is on the classpath.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MdcPropagator.class)
    @ConditionalOnProperty(prefix = "scopeflow.mdc", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class MdcConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public MdcPropagator mdcPropagator(ScopeFlowProperties props) {
            Set<String> keys = props.getMdc().getKeys();
            String prefix = props.getMdc().getPrefix();

            MdcKeyPolicy policy;
            if (keys.isEmpty()) {
                policy = prefix.isEmpty()
                        ? MdcKeyPolicy.allowAll()
                        : MdcKeyPolicy.allowAll(prefix);
            } else {
                policy = prefix.isEmpty()
                        ? MdcKeyPolicy.of(keys)
                        : MdcKeyPolicy.of(keys, prefix);
            }

            return new MdcPropagator(policy);
        }
    }
}
