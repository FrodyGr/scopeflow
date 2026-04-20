/*
 * Copyright 2026 ScopeFlow Contributors
 */
package io.scopeflow.scoped;

import io.scopeflow.core.ScopeFlow;
import io.scopeflow.core.ScopeFlowBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

@SuppressWarnings("preview")
@DisplayName("ScopeFlowScoped")
class ScopeFlowScopedTest {

    private ScopeFlow scopeFlow;

    @BeforeEach
    void setUp() {
        scopeFlow = ScopeFlowBuilder.create().build();
    }

    @Test
    @DisplayName("run should bind context via ScopedValue")
    void runBindsContext() {
        AtomicReference<Map<String, Object>> captured = new AtomicReference<>();

        ScopeFlowScoped.run(scopeFlow, "test",
                Map.of("request.id", "sv-123"), () -> {
                    captured.set(ScopeFlowScoped.contextMap());
                });

        assertThat(captured.get()).containsEntry("request.id", "sv-123");
    }

    @Test
    @DisplayName("run should also open ScopeFlow scope for propagators")
    void runOpensScopeFlowScope() {
        AtomicReference<String> scopeFlowValue = new AtomicReference<>();

        ScopeFlowScoped.run(scopeFlow, "test",
                Map.of("key", "value"), () -> {
                    scopeFlowValue.set(
                            scopeFlow.currentContext().get("key").orElse("MISSING"));
                });

        assertThat(scopeFlowValue.get()).isEqualTo("value");
    }

    @Test
    @DisplayName("call should return result and bind context")
    void callReturnsResult() throws Exception {
        String result = ScopeFlowScoped.call(scopeFlow, "test",
                Map.of("request.id", "call-123"), () -> {
                    return "Result: " + ScopeFlowScoped.contextMap().get("request.id");
                });

        assertThat(result).isEqualTo("Result: call-123");
    }

    @Test
    @DisplayName("nested run should inherit parent context")
    void nestedRunInherits() {
        AtomicReference<Map<String, Object>> innerContext = new AtomicReference<>();

        ScopeFlowScoped.run(scopeFlow, "outer",
                Map.of("parent.key", "parent-val"), () -> {
                    ScopeFlowScoped.run(scopeFlow, "inner",
                            Map.of("child.key", "child-val"), () -> {
                                innerContext.set(Map.copyOf(ScopeFlowScoped.contextMap()));
                            });
                });

        assertThat(innerContext.get())
                .containsEntry("parent.key", "parent-val")
                .containsEntry("child.key", "child-val");
    }

    @Test
    @DisplayName("context should be empty outside of run/call")
    void emptyOutsideScope() {
        assertThat(ScopeFlowScoped.contextMap()).isEmpty();
        assertThat(ScopeFlowScoped.context().isEmpty()).isTrue();
    }

    @Test
    @DisplayName("executeAll should run tasks concurrently and return results")
    void executeAllRunsConcurrently() throws Exception {
        List<String> results = ScopeFlowScoped.executeAll(scopeFlow, List.of(
                () -> "result-1",
                () -> "result-2",
                () -> "result-3"
        ));

        assertThat(results).containsExactly("result-1", "result-2", "result-3");
    }

    @Test
    @DisplayName("executeAll should propagate context to forked tasks")
    void executeAllPropagatesContext() throws Exception {
        try (var scope = scopeFlow.open("parent", Map.of("request.id", "sts-123"))) {
            List<String> results = ScopeFlowScoped.executeAll(scopeFlow, List.of(
                    () -> scopeFlow.currentContext().get("request.id").orElse("MISSING"),
                    () -> scopeFlow.currentContext().get("request.id").orElse("MISSING")
            ));

            assertThat(results).containsExactly("sts-123", "sts-123");
        }
    }
}
