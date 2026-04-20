/*
 * Copyright 2026 ScopeFlow Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.scopeflow.otel;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.BaggageBuilder;
import io.opentelemetry.api.baggage.BaggageEntryMetadata;
import io.opentelemetry.context.Context;
import io.scopeflow.core.Propagator;
import io.scopeflow.core.ScopeContext;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A {@link Propagator} that bridges ScopeFlow context entries to
 * OpenTelemetry {@link Baggage}.
 *
 * <p>When a scope is opened, context entries are added to the current
 * OTel Baggage. When the scope is closed, the previous Baggage is restored.
 * This allows ScopeFlow context to propagate automatically through
 * OpenTelemetry's instrumented HTTP clients, messaging, and gRPC channels.</p>
 *
 * <pre>{@code
 * OtelBaggagePropagator propagator = OtelBaggagePropagator.create();
 * // or with key filtering:
 * OtelBaggagePropagator propagator = OtelBaggagePropagator.create(Set.of("request.id", "tenant.id"));
 *
 * ScopeFlow scopeFlow = ScopeFlowBuilder.create()
 *     .propagator(propagator)
 *     .build();
 * }</pre>
 *
 * @since 0.3.0
 */
public final class OtelBaggagePropagator implements Propagator {

    private static final String PROPAGATOR_NAME = "otel-baggage";
    private static final BaggageEntryMetadata SCOPEFLOW_META =
            BaggageEntryMetadata.create("scopeflow");

    private static final ThreadLocal<ArrayDeque<io.opentelemetry.context.Scope>> OTEL_SCOPE_STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    private final Set<String> allowedKeys;
    private final boolean allowAll;

    private OtelBaggagePropagator(Set<String> allowedKeys, boolean allowAll) {
        this.allowedKeys = allowedKeys;
        this.allowAll = allowAll;
    }

    /** Creates a propagator that bridges all ScopeFlow context keys to Baggage. */
    public static OtelBaggagePropagator create() {
        return new OtelBaggagePropagator(Set.of(), true);
    }

    /** Creates a propagator that only bridges the specified keys to Baggage. */
    public static OtelBaggagePropagator create(Set<String> allowedKeys) {
        Objects.requireNonNull(allowedKeys, "allowedKeys must not be null");
        return new OtelBaggagePropagator(Set.copyOf(allowedKeys), false);
    }

    private boolean shouldPropagate(String key) {
        return allowAll || allowedKeys.contains(key);
    }

    @Override
    public String name() {
        return PROPAGATOR_NAME;
    }

    @Override
    public void onScopeOpened(String scopeName, ScopeContext context) {
        makeBaggageCurrent(context);
    }

    @Override
    public void onScopeEnriched(String key, Object value, ScopeContext context) {
        if (shouldPropagate(key)) {
            // Close current OTel scope to restore parent context, then rebuild
            ArrayDeque<io.opentelemetry.context.Scope> stack = OTEL_SCOPE_STACK.get();
            if (!stack.isEmpty()) {
                stack.pop().close();
            }
            makeBaggageCurrent(context);
        }
    }

    @Override
    public void onScopeClosing(String scopeName, ScopeContext context) {
        // no-op
    }

    @Override
    public void onScopeClosed(String scopeName, ScopeContext restoredContext) {
        ArrayDeque<io.opentelemetry.context.Scope> stack = OTEL_SCOPE_STACK.get();
        if (!stack.isEmpty()) {
            stack.pop().close(); // restores previous OTel context
        }
        if (stack.isEmpty()) {
            OTEL_SCOPE_STACK.remove();
        }
    }

    @Override
    public int order() {
        return 100; // run after MDC
    }

    private void makeBaggageCurrent(ScopeContext context) {
        BaggageBuilder builder = Baggage.current().toBuilder();
        for (Map.Entry<String, Object> entry : context.asMap().entrySet()) {
            if (shouldPropagate(entry.getKey())) {
                builder.put(entry.getKey(), String.valueOf(entry.getValue()), SCOPEFLOW_META);
            }
        }
        io.opentelemetry.context.Scope otelScope =
                Context.current().with(builder.build()).makeCurrent();
        OTEL_SCOPE_STACK.get().push(otelScope);
    }
}
