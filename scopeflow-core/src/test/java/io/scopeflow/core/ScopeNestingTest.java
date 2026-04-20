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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Scope Nesting")
class ScopeNestingTest {

    private ScopeFlow scopeFlow;

    @BeforeEach
    void setUp() {
        scopeFlow = ScopeFlowBuilder.create().build();
    }

    @Test
    @DisplayName("child scope should inherit parent values")
    void childInheritsParentValues() {
        try (Scope parent = scopeFlow.open("parent")) {
            parent.put("request.id", "abc-123");

            try (Scope child = scopeFlow.open("child")) {
                assertThat(scopeFlow.currentContext().get("request.id"))
                        .hasValue("abc-123");
            }
        }
    }

    @Test
    @DisplayName("child scope can override parent values")
    void childCanOverrideParentValues() {
        try (Scope parent = scopeFlow.open("parent")) {
            parent.put("key", "parent-value");

            try (Scope child = scopeFlow.open("child")) {
                child.put("key", "child-value");

                assertThat(scopeFlow.currentContext().get("key"))
                        .hasValue("child-value");
            }

            // Parent value should be restored
            assertThat(scopeFlow.currentContext().get("key"))
                    .hasValue("parent-value");
        }
    }

    @Test
    @DisplayName("child scope values should not leak to parent")
    void childValuesDontLeakToParent() {
        try (Scope parent = scopeFlow.open("parent")) {
            parent.put("parent.key", "parent-value");

            try (Scope child = scopeFlow.open("child")) {
                child.put("child.key", "child-value");

                assertThat(scopeFlow.currentContext().contains("child.key")).isTrue();
                assertThat(scopeFlow.currentContext().contains("parent.key")).isTrue();
            }

            assertThat(scopeFlow.currentContext().contains("parent.key")).isTrue();
            assertThat(scopeFlow.currentContext().contains("child.key")).isFalse();
        }
    }

    @Test
    @DisplayName("three levels of nesting should work correctly")
    void threeLevelsOfNesting() {
        try (Scope s1 = scopeFlow.open("level1")) {
            s1.put("l1", "v1");

            try (Scope s2 = scopeFlow.open("level2")) {
                s2.put("l2", "v2");

                try (Scope s3 = scopeFlow.open("level3")) {
                    s3.put("l3", "v3");

                    ScopeContext ctx = scopeFlow.currentContext();
                    assertThat(ctx.get("l1")).hasValue("v1");
                    assertThat(ctx.get("l2")).hasValue("v2");
                    assertThat(ctx.get("l3")).hasValue("v3");
                    assertThat(ctx.size()).isEqualTo(3);
                }

                ScopeContext ctx = scopeFlow.currentContext();
                assertThat(ctx.get("l1")).hasValue("v1");
                assertThat(ctx.get("l2")).hasValue("v2");
                assertThat(ctx.contains("l3")).isFalse();
                assertThat(ctx.size()).isEqualTo(2);
            }

            ScopeContext ctx = scopeFlow.currentContext();
            assertThat(ctx.get("l1")).hasValue("v1");
            assertThat(ctx.contains("l2")).isFalse();
            assertThat(ctx.size()).isEqualTo(1);
        }

        assertThat(scopeFlow.currentContext().isEmpty()).isTrue();
    }

    @Test
    @DisplayName("opening child with initial values should merge with parent")
    void childInitialValuesMergeWithParent() {
        try (Scope parent = scopeFlow.open("parent", Map.of("a", "1"))) {
            try (Scope child = scopeFlow.open("child", Map.of("b", "2"))) {
                ScopeContext ctx = scopeFlow.currentContext();
                assertThat(ctx.get("a")).hasValue("1");
                assertThat(ctx.get("b")).hasValue("2");
            }
        }
    }

    @Test
    @DisplayName("context should be restored after exception in nested scope")
    void contextRestoredAfterException() {
        try (Scope parent = scopeFlow.open("parent")) {
            parent.put("key", "parent-value");

            try {
                try (Scope child = scopeFlow.open("child")) {
                    child.put("key", "child-value");
                    throw new RuntimeException("boom");
                }
            } catch (RuntimeException ignored) {
                // expected
            }

            // Parent context should be restored
            assertThat(scopeFlow.currentContext().get("key"))
                    .hasValue("parent-value");
        }

        assertThat(scopeFlow.currentContext().isEmpty()).isTrue();
    }
}
