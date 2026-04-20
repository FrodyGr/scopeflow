# Core Concepts

## Architecture

```
┌─────────────────────────────────────────────────┐
│                  ScopeFlow                      │
│  (Facade: open, capture, wrap, currentContext)  │
├─────────────────────────────────────────────────┤
│  Scope Stack (ThreadLocal<ArrayDeque<Scope>>)   │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐        │
│  │ Scope 3  │ │ Scope 2  │ │ Scope 1  │  ...   │
│  │ (inner)  │ │ (middle) │ │ (root)   │        │
│  └──────────┘ └──────────┘ └──────────┘        │
├─────────────────────────────────────────────────┤
│             PropagatorRegistry                  │
│  ┌────────┐ ┌────────────┐ ┌──────────┐        │
│  │  MDC   │ │   OTel     │ │ Custom   │  ...   │
│  └────────┘ └────────────┘ └──────────┘        │
└─────────────────────────────────────────────────┘
```

## Key Interfaces

### ScopeFlow

The main facade. You interact with ScopeFlow through this interface:

```java
public interface ScopeFlow {
    // Open a scope (with try-with-resources)
    Scope open(String name);
    Scope open(String name, Map<String, ?> initialValues);

    // Access current context (read-only)
    ScopeContext currentContext();

    // Capture a snapshot for async propagation
    ScopeSnapshot capture();

    // Wrap tasks for async context propagation
    Runnable wrap(Runnable task);
    <T> Callable<T> wrap(Callable<T> task);
    <T> Supplier<T> wrap(Supplier<T> supplier);

    // Convenience methods
    void run(String name, Map<String, ?> values, Runnable task);
    <T> T call(String name, Map<String, ?> values, Callable<T> task) throws Exception;

    // Wrap executors
    Executor wrapExecutor(Executor executor);
    ExecutorService wrapExecutorService(ExecutorService executorService);
}
```

### Scope

An `AutoCloseable` scope that represents a unit of work. Created by `ScopeFlow.open()`.

```java
public interface Scope extends AutoCloseable {
    // Fluent enrichment
    Scope put(String key, Object value);

    // Read-only context access
    ScopeContext context();

    // Scope metadata
    String name();
    boolean isClosed();

    // AutoCloseable (no checked exception)
    @Override
    void close();
}
```

**Key behaviors:**
- **Fluent API**: `scope.put("a", 1).put("b", 2)` chains naturally
- **Idempotent close**: Calling `close()` multiple times is safe
- **try-with-resources**: Always use in a try-with-resources block

### ScopeContext

A read-only view of the current context values (current scope + all parent scopes):

```java
ScopeContext ctx = scopeFlow.currentContext();

// String access
Optional<String> requestId = ctx.get("request.id");

// Typed access
Optional<Integer> retryCount = ctx.get("retry.count", Integer.class);

// Map export
Map<String, Object> allValues = ctx.asMap();

// Checks
boolean hasKey = ctx.contains("request.id");
boolean empty = ctx.isEmpty();
int count = ctx.size();
```

### Propagator (SPI)

The extension point for integrating external context mechanisms:

```java
public interface Propagator {
    String name();   // Unique propagator identifier

    // Lifecycle hooks
    void onScopeOpened(String scopeName, ScopeContext context);
    void onScopeEnriched(String key, Object value, ScopeContext context);
    void onScopeClosing(String scopeName, ScopeContext context);
    void onScopeClosed(String scopeName, ScopeContext restoredContext);

    // Ordering (lower = runs first)
    default int order() { return 0; }
}
```

**Built-in propagators:**
| Propagator | Module | order() | Description |
|------------|--------|---------|-------------|
| `MdcPropagator` | `scopeflow-mdc` | -100 | SLF4J MDC bridge |
| `OtelBaggagePropagator` | `scopeflow-otel` | 100 | OpenTelemetry Baggage |

### ScopeSnapshot

A capturable/restorable snapshot of the current context for async propagation:

```java
// Capture on the calling thread
ScopeSnapshot snapshot = scopeFlow.capture();

// Restore on a different thread
Scope restoredScope = snapshot.open("async.task");
try {
    // Context values are available here
} finally {
    restoredScope.close();
}
```

### ContextKeyPolicy

Controls which keys are allowed in the context:

```java
// Allow-list: only specific keys allowed
ContextKeyPolicy policy = ContextKeyPolicy.allowList(Set.of("request.id", "tenant.id"));

// Deny-list: everything except specific keys
ContextKeyPolicy policy = ContextKeyPolicy.denyList(Set.of("password", "secret"));

// Allow all (default)
ContextKeyPolicy policy = ContextKeyPolicy.allowAll();
```

---

## Scope Lifecycle

```
ScopeFlow.open("name", values)
    │
    ├── 1. Create ScopeContext (inherit parent + add new values)
    ├── 2. Push onto scope stack (ThreadLocal)
    ├── 3. Notify propagators: onScopeOpened()
    │       └── MDC: save previous values, set new ones
    │       └── OTel: create Baggage, makeCurrent()
    │
    ├── User code runs...
    │   ├── scope.put("key", "value")
    │   │   └── Notify propagators: onScopeEnriched()
    │   ├── scopeFlow.currentContext() → reads current scope
    │   └── ...
    │
    scope.close()
    │
    ├── 4. Notify propagators: onScopeClosing() (context still active)
    ├── 5. Pop from scope stack
    ├── 6. Notify propagators: onScopeClosed() (previous context restored)
    │       └── MDC: restore saved values
    │       └── OTel: close OTel Scope (restores previous Baggage)
    └── Done
```

## Thread Safety

- **ScopeFlow** is thread-safe and designed for concurrent use
- Each thread (including virtual threads) has its own **independent scope stack** via `ThreadLocal`
- **10,000+ virtual threads** can operate concurrently without interference
- **Propagators** must be thread-safe (they run on the caller's thread)
- **ScopeContext** is effectively read-only and safe to share across threads

## Scope Nesting & Inheritance

```java
try (Scope s1 = scopeFlow.open("L1", Map.of("a", "1"))) {
    // context: {a=1}

    try (Scope s2 = scopeFlow.open("L2", Map.of("b", "2"))) {
        // context: {a=1, b=2}  (b inherited from parent)

        try (Scope s3 = scopeFlow.open("L3", Map.of("a", "override"))) {
            // context: {a=override, b=2}  (a overridden, b inherited)
        }
        // context: {a=1, b=2}  (a restored to L2's value)
    }
    // context: {a=1}  (b removed)
}
// context: {} (empty)
```

**Rules:**
1. Child scopes **inherit** all parent values
2. Child values **override** parent values with the same key
3. Scope close **restores** the parent's state exactly
4. Scopes **must close in LIFO order** (enforced by try-with-resources)
