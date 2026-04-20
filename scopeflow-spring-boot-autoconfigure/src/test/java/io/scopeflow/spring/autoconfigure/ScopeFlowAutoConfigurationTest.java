/*
 * Copyright 2026 ScopeFlow Contributors
 */
package io.scopeflow.spring.autoconfigure;

import io.scopeflow.core.ScopeFlow;
import io.scopeflow.mdc.MdcPropagator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ScopeFlowAutoConfiguration")
class ScopeFlowAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ScopeFlowAutoConfiguration.class));

    @Nested
    @DisplayName("ScopeFlow bean")
    class ScopeFlowBeanTests {

        @Test
        @DisplayName("should create ScopeFlow bean by default")
        void createsBean() {
            contextRunner.run(context -> {
                assertThat(context).hasSingleBean(ScopeFlow.class);
            });
        }

        @Test
        @DisplayName("should not create ScopeFlow when disabled")
        void disabledByProperty() {
            contextRunner
                    .withPropertyValues("scopeflow.enabled=false")
                    .run(context -> {
                        assertThat(context).doesNotHaveBean(ScopeFlow.class);
                    });
        }
    }

    @Nested
    @DisplayName("MDC auto-configuration")
    class MdcTests {

        @Test
        @DisplayName("should create MdcPropagator when scopeflow-mdc is on classpath")
        void createsMdcPropagator() {
            contextRunner.run(context -> {
                assertThat(context).hasSingleBean(MdcPropagator.class);
            });
        }

        @Test
        @DisplayName("should not create MdcPropagator when disabled")
        void mdcDisabledByProperty() {
            contextRunner
                    .withPropertyValues("scopeflow.mdc.enabled=false")
                    .run(context -> {
                        assertThat(context).doesNotHaveBean(MdcPropagator.class);
                    });
        }

        @Test
        @DisplayName("should configure MDC keys from properties")
        void mdcKeysFromProperties() {
            contextRunner
                    .withPropertyValues("scopeflow.mdc.keys=request.id,tenant.id")
                    .run(context -> {
                        MdcPropagator propagator = context.getBean(MdcPropagator.class);
                        assertThat(propagator.policy().shouldPropagate("request.id")).isTrue();
                        assertThat(propagator.policy().shouldPropagate("tenant.id")).isTrue();
                        assertThat(propagator.policy().shouldPropagate("other.key")).isFalse();
                    });
        }

        @Test
        @DisplayName("should configure MDC prefix from properties")
        void mdcPrefixFromProperties() {
            contextRunner
                    .withPropertyValues("scopeflow.mdc.prefix=sf.")
                    .run(context -> {
                        MdcPropagator propagator = context.getBean(MdcPropagator.class);
                        assertThat(propagator.policy().mdcKey("request.id"))
                                .isEqualTo("sf.request.id");
                    });
        }

        @Test
        @DisplayName("should default to allowAll when no keys configured")
        void defaultAllowAll() {
            contextRunner.run(context -> {
                MdcPropagator propagator = context.getBean(MdcPropagator.class);
                assertThat(propagator.policy().isAllowAll()).isTrue();
            });
        }
    }

    @Nested
    @DisplayName("MVC interceptor")
    class MvcTests {

        private final WebApplicationContextRunner webRunner = new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ScopeFlowAutoConfiguration.class,
                        ScopeFlowWebMvcAutoConfiguration.class));

        @Test
        @DisplayName("should create MVC interceptor for web apps")
        void createsMvcInterceptor() {
            webRunner.run(context -> {
                assertThat(context).hasSingleBean(ScopeFlowMvcInterceptor.class);
            });
        }

        @Test
        @DisplayName("should create TaskDecorator")
        void createsTaskDecorator() {
            webRunner.run(context -> {
                assertThat(context).hasSingleBean(ScopeFlowTaskDecorator.class);
            });
        }

        @Test
        @DisplayName("should not create MVC interceptor when disabled")
        void disabledMvcInterceptor() {
            webRunner
                    .withPropertyValues("scopeflow.http.server.generate-request-scope=false")
                    .run(context -> {
                        assertThat(context).doesNotHaveBean(ScopeFlowMvcInterceptor.class);
                    });
        }
    }
}
