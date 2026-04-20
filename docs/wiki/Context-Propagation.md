# Context Propagation

## The Problem

In modern Java applications with virtual threads, thread pools, and async frameworks, **ThreadLocal values are lost** when work crosses thread boundaries:

```java
MDC.put("request.id", "abc-123");
executor.submit(() -> {
    MDC.get("request.id"); // null! Context is lost.
});
```

ScopeFlow solves this with transparent context propagation.

---

## Task Wrappers

### Wrapping individual tasks

```java
// Runnable
Runnable wrapped = scopeFlow.wrap(() -> {
    log.info("Context available here!");
});

// Callable
Callable<String> wrapped = scopeFlow.wrap(() -> {
    return scopeFlow.currentContext().get("request.id").orElse("?");
});

// Supplier
Supplier<String> wrapped = scopeFlow.wrap((Supplier<String>) () -> {
    return "Result with context";
});
```

### Wrapping executors

```java
// Wrap any Executor — all submitted tasks get context automatically
Executor wrapped = scopeFlow.wrapExecutor(Executors.newVirtualThreadPerTaskExecutor());
wrapped.execute(() -> {
    // Context is here!
});

// Wrap ExecutorService — all submit/invoke methods propagate context
ExecutorService wrapped = scopeFlow.wrapExecutorService(
    Executors.newFixedThreadPool(4));
Future<String> future = wrapped.submit(() -> {
    return scopeFlow.currentContext().get("request.id").orElse("missing");
});
```

### How wrappers work internally

```
Thread A (opener)              Thread B (executor)
─────────────────              ────────────────────
scopeFlow.open("req", values)
    │
scopeFlow.wrap(task)
    ├── snapshot = capture()     
    ├── context frozen           
    │                            task.run()
    │                              ├── scope = snapshot.open()
    │                              ├── propagators fired (MDC set)
    │                              ├── original task executes
    │                              ├── scope.close()
    │                              └── MDC cleaned up
```

---

## Virtual Threads

ScopeFlow is designed specifically for Java 21+ virtual threads:

### ThreadLocal per virtual thread

Each virtual thread gets its own **independent scope stack** via `ThreadLocal`. This means:
- No shared mutable state
- No synchronization needed
- No lock contention
- Scales to millions of virtual threads

### Example with 10,000 concurrent requests

```java
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    var wrapped = scopeFlow.wrapExecutorService(executor);

    IntStream.range(0, 10_000)
        .parallel()
        .forEach(i -> wrapped.submit(() -> {
            try (Scope scope = scopeFlow.open("request",
                    Map.of("request.id", "req-" + i))) {
                // Each virtual thread has its own isolated context
                // No cross-contamination between requests
                assertThat(scopeFlow.currentContext().get("request.id"))
                    .hasValue("req-" + i);
            }
        }));
}
```

### Why not just use InheritableThreadLocal?

`InheritableThreadLocal` has issues with virtual threads:
1. **Stale values**: Child threads inherit the parent's value at creation time, not at execution time
2. **Memory leaks**: Platform thread pools reuse threads, so inherited values may persist
3. **Unpredictable**: With virtual threads, the carrier thread may change, making ITL unreliable

ScopeFlow uses explicit snapshot-based propagation, which is:
- **Predictable**: Values captured at wrap-time, restored at execution-time
- **Clean**: Values always cleaned up via scope lifecycle
- **Safe**: No memory leaks from stale ThreadLocal values

---

## Snapshot API

For custom propagation scenarios:

```java
// Capture current context
ScopeSnapshot snapshot = scopeFlow.capture();

// Use wherever you need the context restored
CompletableFuture.supplyAsync(() -> {
    try (Scope restored = snapshot.open("async.compute")) {
        // All parent context values are available
        return compute();
    }
}, executor);

// Snapshots are immutable and can be stored/shared
cache.put("context-" + requestId, snapshot);
```

---

## CompletableFuture Integration

```java
ScopeSnapshot snapshot = scopeFlow.capture();

CompletableFuture
    .supplyAsync(scopeFlow.wrap(() -> fetchData()), executor)
    .thenApplyAsync(scopeFlow.wrap(data -> transform(data)), executor)
    .thenAcceptAsync(scopeFlow.wrap(result -> save(result)), executor)
    .join();
```

---

## Spring @Async

With the Spring Boot starter, `@Async` context propagation is **automatic**:

```java
@Service
public class OrderService {

    @Async
    public CompletableFuture<String> processAsync(String orderId) {
        // ScopeFlow context is automatically available here
        // MDC has request.id from the calling thread
        log.info("Processing order {} async", orderId);
        return CompletableFuture.completedFuture("OK");
    }
}
```

No additional configuration needed. The `ScopeFlowTaskDecorator` is auto-applied to all Spring-managed task executors.
