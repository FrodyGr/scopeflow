/*
 * Copyright 2026 ScopeFlow Contributors
 */
package io.scopeflow.micrometer;

import io.scopeflow.core.Scope;
import io.scopeflow.core.ScopeFlow;
import io.scopeflow.core.ScopeFlowBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ScopeFlowThreadLocalAccessor")
class ScopeFlowThreadLocalAccessorTest {

    private ScopeFlow scopeFlow;
    private ScopeFlowThreadLocalAccessor accessor;

    @BeforeEach
    void setUp() {
        scopeFlow = ScopeFlowBuilder.create().build();
        accessor = new ScopeFlowThreadLocalAccessor(scopeFlow);
    }

    @Test
    @DisplayName("key should return correct identifier")
    void keyReturnsIdentifier() {
        assertThat(accessor.key()).isEqualTo("scopeflow.context");
    }

    @Test
    @DisplayName("getValue should return null when no scope is active")
    void getValueReturnsNullWhenEmpty() {
        assertThat(accessor.getValue()).isNull();
    }

    @Test
    @DisplayName("getValue should capture current scope context")
    void getValueReturnsContextValues() {
        try (Scope scope = scopeFlow.open("test", Map.of("request.id", "abc"))) {
            Map<String, Object> captured = accessor.getValue();
            assertThat(captured).containsEntry("request.id", "abc");
        }
    }

    @Test
    @DisplayName("setValue should restore captured context")
    void setValueRestoresContext() {
        Map<String, Object> values = Map.of("request.id", "restored-id", "tenant.id", "acme");
        accessor.setValue(values);

        assertThat(scopeFlow.currentContext().get("request.id")).hasValue("restored-id");
        assertThat(scopeFlow.currentContext().get("tenant.id")).hasValue("acme");

        accessor.setValue(); // cleanup
        assertThat(scopeFlow.currentContext().isEmpty()).isTrue();
    }

    @Test
    @DisplayName("setValue() should close restored scope")
    void resetClearsRestoredScope() {
        accessor.setValue(Map.of("key", "value"));
        assertThat(scopeFlow.currentContext().isEmpty()).isFalse();

        accessor.setValue();
        assertThat(scopeFlow.currentContext().isEmpty()).isTrue();
    }

    @Test
    @DisplayName("successive setValue calls should replace previous scope")
    void setValueReplacesExisting() {
        accessor.setValue(Map.of("request.id", "first"));
        assertThat(scopeFlow.currentContext().get("request.id")).hasValue("first");

        accessor.setValue(Map.of("request.id", "second"));
        assertThat(scopeFlow.currentContext().get("request.id")).hasValue("second");

        accessor.setValue();
        assertThat(scopeFlow.currentContext().isEmpty()).isTrue();
    }

    @Test
    @DisplayName("full capture-restore cycle")
    void captureAndRestore() {
        // Simulate: capture on one thread, restore on another
        Map<String, Object> captured;
        try (Scope scope = scopeFlow.open("http.request",
                Map.of("request.id", "cap-123"))) {
            captured = accessor.getValue();
        }

        // Simulate: restore on a different thread/reactive boundary
        assertThat(scopeFlow.currentContext().isEmpty()).isTrue();
        accessor.setValue(captured);
        assertThat(scopeFlow.currentContext().get("request.id")).hasValue("cap-123");
        accessor.setValue();
        assertThat(scopeFlow.currentContext().isEmpty()).isTrue();
    }
}
