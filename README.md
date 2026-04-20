# ScopeFlow

**The simplest way to propagate context, logging and tracing in Java 21+ with Spring and virtual threads.**

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org/jeps/444)

---

## What is ScopeFlow?

ScopeFlow is an open-source library that simplifies **execution context propagation** across technical and business layers in modern Java applications. It bridges the gap between logging (MDC), observability (Micrometer, OpenTelemetry), and modern concurrency (virtual threads, `ScopedValue`).

### The Problem

In Java/Spring applications, context (request IDs, tenant IDs, user IDs, trace info) lives scattered across MDC, ThreadLocal, Reactor Context, Baggage, and Spans. Propagating it consistently — especially across async boundaries and virtual threads — requires boilerplate and is error-prone.

### The Solution

ScopeFlow provides a single **scope-based abstraction** that:

- ✅ Creates, enriches, and closes context scopes
- ✅ Auto-propagates to MDC for correlated logging
- ✅ Wraps tasks for cross-thread context propagation
- ✅ Integrates with Spring Boot via a starter
- ✅ Works with Micrometer and OpenTelemetry (without replacing them)
- ✅ Supports virtual threads out of the box
- ✅ Cleans up context automatically via `try-with-resources`

---

## Quick Start

### 1. Add the dependency

```xml
<dependency>
    <groupId>io.scopeflow</groupId>
    <artifactId>scopeflow-core</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>

<!-- For MDC integration -->
<dependency>
    <groupId>io.scopeflow</groupId>
    <artifactId>scopeflow-mdc</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### 2. Create a ScopeFlow instance

```java
import io.scopeflow.core.*;
import io.scopeflow.mdc.*;

// With MDC propagation
ScopeFlow scopeFlow = ScopeFlowBuilder.create()
    .propagator(new MdcPropagator(
        MdcKeyPolicy.of(Set.of("request.id", "tenant.id"))
    ))
    .build();
```

### 3. Use scopes in your code

```java
// Open a scope — context is propagated to MDC automatically
try (Scope scope = scopeFlow.open("http.request")
        .put("request.id", requestId)
        .put("tenant.id", tenantId)) {

    log.info("Processing request");  // MDC contains request.id and tenant.id
    service.process();
}
// MDC is automatically cleaned up
```

### 4. Propagate across threads

```java
// Wrap a task for cross-thread propagation
try (Scope scope = scopeFlow.open("http.request")
        .put("request.id", requestId)) {

    executor.submit(scopeFlow.wrap(() -> {
        // Context is available here, even on a different (virtual) thread
        log.info("Async task");  // MDC has request.id
    }));
}
```

### 5. Use convenience methods

```java
// One-liner scope execution
scopeFlow.run("order.process", Map.of("order.id", orderId), () -> {
    service.processOrder();
});

// With return value
String result = scopeFlow.call("lookup", Map.of("key", key), () -> {
    return repository.findById(key);
});
```

---

## Modules

| Module | Description | Status |
|--------|-------------|--------|
| `scopeflow-core` | Core API, scope lifecycle, wrappers | ✅ v0.1 |
| `scopeflow-mdc` | SLF4J MDC bridge | ✅ v0.1 |
| `scopeflow-micrometer` | Micrometer Context Propagation | 🔜 v0.3 |
| `scopeflow-otel` | OpenTelemetry bridge | 🔜 v0.3 |
| `scopeflow-spring-boot-starter` | Spring Boot auto-configuration | 🔜 v0.2 |
| `scopeflow-java25` | ScopedValue support (JDK 25+) | 🔜 v0.4 |
| `scopeflow-bom` | Bill of Materials | ✅ v0.1 |

---

## Key Features

### Scope Nesting

Scopes nest naturally. Child scopes inherit parent values and restore them on close:

```java
try (Scope request = scopeFlow.open("http.request")
        .put("request.id", "abc-123")) {

    try (Scope order = scopeFlow.open("order.process")
            .put("order.id", "ORD-456")) {
        // context has both request.id and order.id
    }
    // only request.id remains
}
// context is empty
```

### Executor Wrappers

Wrap entire executors for automatic context propagation:

```java
import io.scopeflow.core.wrap.*;

Executor wrapped = new ContextExecutor(originalExecutor, scopeFlow);
ExecutorService wrappedService = new ContextExecutorService(executorService, scopeFlow);

// All submitted tasks automatically carry context
wrappedService.submit(() -> {
    log.info("Context is here!");
});
```

### Key Policies

Control which keys are allowed in scopes:

```java
ScopeFlow scopeFlow = ScopeFlowBuilder.create()
    .keyPolicy(ContextKeyPolicy.denyList(Set.of("password", "token")))
    .build();
```

### Snapshots

Capture and restore context manually:

```java
ScopeSnapshot snapshot = scopeFlow.capture();

// Later, on any thread:
try (Scope scope = snapshot.restore("restored")) {
    // original context is active
}
```

---

## Requirements

- **Java 21** or later (for virtual threads)
- **SLF4J 2.x** (for MDC module)
- **Spring Boot 3.2+** (for starter — coming in v0.2)

---

## Building from Source

```bash
mvn clean verify
```

---

## Roadmap

- **v0.1.0** — Core + MDC + Wrappers ← *current*
- **v0.2.0** — Spring Boot Starter + MVC Interceptor
- **v0.3.0** — Micrometer + OpenTelemetry integrations
- **v0.4.0** — Java 25 ScopedValue support
- **v0.5.0** — Structured Concurrency preview module
- **v1.0.0** — Stable API release

---

## License

[Apache License 2.0](LICENSE)
