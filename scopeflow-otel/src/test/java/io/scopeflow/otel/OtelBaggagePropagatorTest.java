/*
 * Copyright 2026 ScopeFlow Contributors
 */
package io.scopeflow.otel;

import io.opentelemetry.api.baggage.Baggage;
import io.scopeflow.core.Scope;
import io.scopeflow.core.ScopeFlow;
import io.scopeflow.core.ScopeFlowBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

@DisplayName("OtelBaggagePropagator")
class OtelBaggagePropagatorTest {

    @Test
    @DisplayName("should add context entries to OTel Baggage when scope opens")
    void addsToBaggage() {
        ScopeFlow scopeFlow = ScopeFlowBuilder.create()
                .propagator(OtelBaggagePropagator.create())
                .build();

        try (Scope scope = scopeFlow.open("test",
                Map.of("request.id", "abc-123", "tenant.id", "acme"))) {
            Baggage baggage = Baggage.current();
            assertThat(baggage.getEntryValue("request.id")).isEqualTo("abc-123");
            assertThat(baggage.getEntryValue("tenant.id")).isEqualTo("acme");
        }
    }

    @Test
    @DisplayName("should restore previous Baggage when scope closes")
    void restoresBaggageOnClose() {
        ScopeFlow scopeFlow = ScopeFlowBuilder.create()
                .propagator(OtelBaggagePropagator.create())
                .build();

        try (Scope scope = scopeFlow.open("test",
                Map.of("request.id", "abc-123"))) {
            assertThat(Baggage.current().getEntryValue("request.id")).isEqualTo("abc-123");
        }

        assertThat(Baggage.current().getEntryValue("request.id")).isNull();
    }

    @Test
    @DisplayName("should only propagate allowed keys")
    void filtersKeys() {
        ScopeFlow scopeFlow = ScopeFlowBuilder.create()
                .propagator(OtelBaggagePropagator.create(Set.of("request.id")))
                .build();

        try (Scope scope = scopeFlow.open("test",
                Map.of("request.id", "abc", "secret", "password"))) {
            assertThat(Baggage.current().getEntryValue("request.id")).isEqualTo("abc");
            assertThat(Baggage.current().getEntryValue("secret")).isNull();
        }
    }

    @Test
    @DisplayName("should handle nested scopes")
    void nestedScopes() {
        ScopeFlow scopeFlow = ScopeFlowBuilder.create()
                .propagator(OtelBaggagePropagator.create())
                .build();

        try (Scope outer = scopeFlow.open("outer", Map.of("request.id", "outer-id"))) {
            assertThat(Baggage.current().getEntryValue("request.id")).isEqualTo("outer-id");

            try (Scope inner = scopeFlow.open("inner", Map.of("request.id", "inner-id"))) {
                assertThat(Baggage.current().getEntryValue("request.id")).isEqualTo("inner-id");
            }

            assertThat(Baggage.current().getEntryValue("request.id")).isEqualTo("outer-id");
        }

        assertThat(Baggage.current().getEntryValue("request.id")).isNull();
    }

    @Test
    @DisplayName("should update Baggage on enrichment")
    void updatesOnEnrichment() {
        ScopeFlow scopeFlow = ScopeFlowBuilder.create()
                .propagator(OtelBaggagePropagator.create())
                .build();

        try (Scope scope = scopeFlow.open("test")) {
            assertThat(Baggage.current().getEntryValue("request.id")).isNull();

            scope.put("request.id", "enriched-123");
            assertThat(Baggage.current().getEntryValue("request.id")).isEqualTo("enriched-123");
        }

        assertThat(Baggage.current().getEntryValue("request.id")).isNull();
    }
}
