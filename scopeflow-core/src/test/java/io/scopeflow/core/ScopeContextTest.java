/*
 * Copyright 2026 ScopeFlow Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.scopeflow.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ScopeContext")
class ScopeContextTest {

    @Test
    @DisplayName("empty context should have no entries")
    void emptyContext() {
        ScopeContext ctx = ScopeContext.empty();

        assertThat(ctx.isEmpty()).isTrue();
        assertThat(ctx.size()).isEqualTo(0);
        assertThat(ctx.asMap()).isEmpty();
    }

    @Test
    @DisplayName("get should return Optional.empty for missing key")
    void getMissingKey() {
        ScopeContext ctx = ScopeContext.empty();

        assertThat(ctx.get("missing")).isEmpty();
    }

    @Test
    @DisplayName("typed get should return empty for wrong type")
    void typedGetWrongType() {
        ScopeFlow scopeFlow = ScopeFlowBuilder.create().build();

        try (Scope scope = scopeFlow.open("test")) {
            scope.put("key", "string-value");

            assertThat(scope.context().get("key", Integer.class)).isEmpty();
            assertThat(scope.context().get("key", String.class)).hasValue("string-value");
        }
    }

    @Test
    @DisplayName("contains should return true for existing keys")
    void containsExistingKey() {
        ScopeFlow scopeFlow = ScopeFlowBuilder.create().build();

        try (Scope scope = scopeFlow.open("test", Map.of("key", "value"))) {
            assertThat(scope.context().contains("key")).isTrue();
            assertThat(scope.context().contains("other")).isFalse();
        }
    }

    @Test
    @DisplayName("asMap should return unmodifiable copy")
    void asMapIsUnmodifiable() {
        ScopeFlow scopeFlow = ScopeFlowBuilder.create().build();

        try (Scope scope = scopeFlow.open("test", Map.of("key", "value"))) {
            Map<String, Object> map = scope.context().asMap();

            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> map.put("new", "entry"));
        }
    }

    @Test
    @DisplayName("asMap should be a snapshot, not a live view")
    void asMapIsSnapshot() {
        ScopeFlow scopeFlow = ScopeFlowBuilder.create().build();

        try (Scope scope = scopeFlow.open("test")) {
            scope.put("key1", "value1");
            Map<String, Object> snapshot = scope.context().asMap();

            scope.put("key2", "value2");

            // Snapshot should not see key2
            assertThat(snapshot).containsOnlyKeys("key1");
            // But current context should
            assertThat(scope.context().asMap()).containsKeys("key1", "key2");
        }
    }

    @Test
    @DisplayName("size should reflect current entries")
    void sizeReflectsEntries() {
        ScopeFlow scopeFlow = ScopeFlowBuilder.create().build();

        try (Scope scope = scopeFlow.open("test")) {
            assertThat(scope.context().size()).isEqualTo(0);

            scope.put("a", "1");
            assertThat(scope.context().size()).isEqualTo(1);

            scope.put("b", "2");
            assertThat(scope.context().size()).isEqualTo(2);

            // Overwrite should not increase size
            scope.put("a", "updated");
            assertThat(scope.context().size()).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("get String should convert non-string values to string")
    void getStringConvertsTypes() {
        ScopeFlow scopeFlow = ScopeFlowBuilder.create().build();

        try (Scope scope = scopeFlow.open("test")) {
            scope.put("number", 42);
            scope.put("bool", true);

            assertThat(scope.context().get("number")).hasValue("42");
            assertThat(scope.context().get("bool")).hasValue("true");
        }
    }
}
