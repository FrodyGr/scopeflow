# OpenTelemetry Integration

## Overview

The `scopeflow-otel` module bridges ScopeFlow context entries to **OpenTelemetry Baggage**, enabling context propagation through OTel-instrumented HTTP clients, gRPC channels, and messaging systems.

```xml
<dependency>
    <groupId>io.scopeflow</groupId>
    <artifactId>scopeflow-otel</artifactId>
</dependency>
```

---

## Setup

### Propagate all keys

```java
ScopeFlow scopeFlow = ScopeFlowBuilder.create()
    .propagator(new MdcPropagator(MdcKeyPolicy.allowAll()))
    .propagator(OtelBaggagePropagator.create())
    .build();
```

### Propagate specific keys only

```java
ScopeFlow scopeFlow = ScopeFlowBuilder.create()
    .propagator(OtelBaggagePropagator.create(Set.of("request.id", "tenant.id")))
    .build();
```

---

## How It Works

```
ScopeFlow.open("request", {request.id=abc-123})
    │
    └── OtelBaggagePropagator.onScopeOpened()
        ├── Read current Baggage
        ├── Build new Baggage: current + {request.id=abc-123}
        ├── Context.current().with(newBaggage).makeCurrent()
        └── Push OTel Scope onto stack

scope.put("tenant.id", "acme")
    │
    └── OtelBaggagePropagator.onScopeEnriched()
        ├── Close current OTel Scope (restores parent Baggage)
        ├── Build new Baggage: {request.id=abc-123, tenant.id=acme}
        ├── Context.current().with(newBaggage).makeCurrent()
        └── Push new OTel Scope onto stack

scope.close()
    │
    └── OtelBaggagePropagator.onScopeClosed()
        ├── Pop OTel Scope from stack
        ├── scope.close() → restores previous OTel Context
        └── Previous Baggage active again
```

---

## Distributed Tracing Correlation

When your application calls another service, OTel's instrumentation automatically propagates Baggage via HTTP headers:

### Outgoing request (automatic)

```
GET /api/data HTTP/1.1
Host: service-b
baggage: request.id=abc-123,tenant.id=acme
traceparent: 00-trace-id-span-id-01
```

### Receiving service

The receiving service's OTel instrumentation reads the `baggage` header, making the values available via `Baggage.current()`.

---

## Nested Scopes & OTel

```java
try (Scope outer = scopeFlow.open("http",
        Map.of("request.id", "req-1"))) {
    // OTel Baggage: {request.id=req-1}

    try (Scope inner = scopeFlow.open("db",
            Map.of("query.id", "q-1"))) {
        // OTel Baggage: {request.id=req-1, query.id=q-1}

        Baggage.current().getEntryValue("request.id"); // "req-1"
        Baggage.current().getEntryValue("query.id");   // "q-1"
    }
    // OTel Baggage: {request.id=req-1}  (query.id removed)
}
// OTel Baggage: {} (everything restored)
```

---

## Combining with MDC

Both propagators can coexist. MDC runs first (order -100), OTel runs after (order 100):

```java
ScopeFlow scopeFlow = ScopeFlowBuilder.create()
    .propagator(new MdcPropagator(MdcKeyPolicy.of(Set.of("request.id"))))
    .propagator(OtelBaggagePropagator.create(Set.of("request.id", "tenant.id")))
    .build();

try (Scope scope = scopeFlow.open("request",
        Map.of("request.id", "abc", "tenant.id", "acme"))) {

    // MDC has: request.id=abc (tenant.id filtered by MDC policy)
    // OTel Baggage has: request.id=abc, tenant.id=acme

    log.info("Processing");  // Logs show request.id
    httpClient.get("/other-service");  // Baggage propagates both
}
```

---

## Baggage Metadata

All Baggage entries created by ScopeFlow include a metadata tag `"scopeflow"` for easy identification:

```java
Baggage.current().forEach((key, entry) -> {
    if ("scopeflow".equals(entry.getMetadata().getValue())) {
        // This entry was set by ScopeFlow
    }
});
```
