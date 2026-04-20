# ScopedValue & StructuredTaskScope (Preview)

> **⚠️ Preview Feature**: This module requires Java 23+ with `--enable-preview`. APIs may change in future Java releases.

## Overview

The `scopeflow-scoped` module provides an alternative API using Java's **ScopedValue** and **StructuredTaskScope** — designed for applications that want to leverage these modern concurrency primitives.

```xml
<dependency>
    <groupId>io.scopeflow</groupId>
    <artifactId>scopeflow-scoped</artifactId>
</dependency>
```

---

## Why ScopedValue?

| Feature | ThreadLocal | ScopedValue |
|---------|-------------|-------------|
| Mutability | Mutable | Immutable per binding |
| Inheritance | Via InheritableThreadLocal | Via StructuredTaskScope |
| Performance | Good | Better (no inheritance overhead) |
| Structured | No (can leak) | Yes (bound to call stack) |
| Virtual Threads | Works (carefully) | Designed for VT |

ScopedValue guarantees that context is **structured**: values are bound to a specific call stack frame and automatically unbound when that frame exits. No cleanup needed, no leaks possible.

---

## ScopeFlowScoped API

### run() — Execute with context

```java
ScopeFlowScoped.run(scopeFlow, "order.process",
    Map.of("order.id", "ORD-123", "customer.id", "CUST-456"), () -> {

    // Context available via ScopedValue
    Map<String, Object> ctx = ScopeFlowScoped.contextMap();
    // {order.id=ORD-123, customer.id=CUST-456}

    // Also available via ScopeFlow (propagators fire normally)
    scopeFlow.currentContext().get("order.id"); // Optional["ORD-123"]

    // MDC is set if MdcPropagator is configured
    log.info("Processing order"); // [order.id=ORD-123]
});
```

### call() — Execute with context and return value

```java
String result = ScopeFlowScoped.call(scopeFlow, "compute",
    Map.of("request.id", "req-1"), () -> {

    String reqId = ScopeFlowScoped.contextMap().get("request.id").toString();
    return "Computed for " + reqId;
});
// result = "Computed for req-1"
```

### Nested contexts

ScopedValue-based scopes inherit parent values automatically:

```java
ScopeFlowScoped.run(scopeFlow, "parent",
    Map.of("tenant.id", "acme"), () -> {

    ScopeFlowScoped.run(scopeFlow, "child",
        Map.of("user.id", "user-1"), () -> {

        Map<String, Object> ctx = ScopeFlowScoped.contextMap();
        // {tenant.id=acme, user.id=user-1}  — both available!
    });

    Map<String, Object> ctx = ScopeFlowScoped.contextMap();
    // {tenant.id=acme}  — user.id automatically unbound
});
```

---

## StructuredTaskScope

### executeAll() — Concurrent execution with context propagation

Execute multiple tasks concurrently using `StructuredTaskScope.ShutdownOnFailure`:

```java
try (Scope scope = scopeFlow.open("batch",
        Map.of("batch.id", "B-001"))) {

    List<String> results = ScopeFlowScoped.executeAll(scopeFlow, List.of(
        () -> fetchFromServiceA(),  // Context propagated
        () -> fetchFromServiceB(),  // Context propagated
        () -> fetchFromServiceC()   // Context propagated
    ));

    // results: ["A-result", "B-result", "C-result"]
    // All tasks had batch.id=B-001 in their context
}
```

### How it works

```
Parent Thread                    Forked Virtual Threads
───────────────                  ──────────────────────
scopeFlow.open("batch")
    │
ScopeFlowScoped.executeAll(tasks)
    ├── StructuredTaskScope.ShutdownOnFailure()
    ├── for each task:
    │   └── scope.fork(scopeFlow.wrap(task))
    │       ├── Captures snapshot at fork time
    │       └── Restores snapshot in forked thread
    ├── scope.join()                 task A: context available ✓
    │                                task B: context available ✓
    │                                task C: context available ✓
    └── scope.throwIfFailed()
```

### Error handling

If any task fails, `ShutdownOnFailure` interrupts all other tasks and the exception is propagated:

```java
try {
    List<String> results = ScopeFlowScoped.executeAll(scopeFlow, List.of(
        () -> "success",
        () -> { throw new IOException("Service B down"); },
        () -> "never reached"
    ));
} catch (Exception e) {
    // IOException from task 2 is propagated
    // Tasks 1 and 3 were interrupted
}
```

---

## Compiler Configuration

The module requires `--enable-preview`:

```xml
<!-- This is already configured in scopeflow-scoped/pom.xml -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <release>23</release>
        <compilerArgs>
            <arg>--enable-preview</arg>
        </compilerArgs>
    </configuration>
</plugin>
```

To use in your project, add `--enable-preview` to:
1. `maven-compiler-plugin` configuration
2. `maven-surefire-plugin` argLine
3. JVM startup arguments

---

## When to Use ScopeFlowScoped vs ScopeFlow

| Use case | Recommended API |
|----------|----------------|
| Standard try-with-resources scoping | `ScopeFlow.open()` |
| Spring Boot / MVC | `ScopeFlow` (auto-configured) |
| Lambda / functional style | `ScopeFlowScoped.run()` / `.call()` |
| Structured concurrency | `ScopeFlowScoped.executeAll()` |
| Reactive (Reactor/RxJava) | `ScopeFlow` + Micrometer accessor |

Both APIs are complementary. `ScopeFlowScoped` uses `ScopeFlow` internally, so propagators (MDC, OTel) fire in both cases.
