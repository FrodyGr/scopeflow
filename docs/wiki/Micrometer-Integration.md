# Micrometer Integration

## Overview

The `scopeflow-micrometer` module provides a `ThreadLocalAccessor` bridge that enables ScopeFlow context to propagate through **Project Reactor** pipelines, **WebFlux**, and any framework using Micrometer's context propagation.

```xml
<dependency>
    <groupId>io.scopeflow</groupId>
    <artifactId>scopeflow-micrometer</artifactId>
</dependency>
```

---

## Registration

Register the accessor with Micrometer's `ContextRegistry`:

```java
import io.micrometer.context.ContextRegistry;

ScopeFlow scopeFlow = ...; // injected or built manually

ContextRegistry.getInstance()
    .registerThreadLocalAccessor(new ScopeFlowThreadLocalAccessor(scopeFlow));
```

### Spring Boot auto-registration

```java
@Configuration
public class MicrometerConfig {

    @Bean
    public ApplicationRunner registerScopeFlowAccessor(ScopeFlow scopeFlow) {
        return args -> {
            ContextRegistry.getInstance()
                .registerThreadLocalAccessor(new ScopeFlowThreadLocalAccessor(scopeFlow));
        };
    }
}
```

---

## How It Works

The `ScopeFlowThreadLocalAccessor` implements three operations:

| Method | Description |
|--------|-------------|
| `getValue()` | Captures the current ScopeFlow context as an immutable `Map<String, Object>` |
| `setValue(Map)` | Opens a scope named `"micrometer.restore"` with the captured values |
| `setValue()` | Closes the restore scope, cleaning up MDC and context |

### Lifecycle in a Reactor pipeline

```
Mono.deferContextual(ctx -> ...)
    │
    ├── subscribe on Thread A:
    │   └── getValue() → captures {request.id=abc-123}
    │
    ├── execute on Thread B (Reactor scheduler):
    │   ├── setValue({request.id=abc-123}) → opens scope
    │   ├── user code runs with context available
    │   └── setValue() → closes scope, MDC cleaned up
    │
    └── back on Thread A (or another):
        └── context properly cleaned up
```

---

## Project Reactor Example

```java
// Enable automatic context propagation (Reactor 3.5.3+)
Hooks.enableAutomaticContextPropagation();

Mono.just("order-123")
    .flatMap(orderId -> {
        // This runs on a Reactor scheduler thread
        // ScopeFlow context is automatically restored!
        log.info("Processing {}", orderId); // MDC has request.id
        return processOrder(orderId);
    })
    .subscribeOn(Schedulers.boundedElastic())
    .subscribe();
```

---

## WebFlux Integration

For Spring WebFlux applications, the Micrometer bridge enables ScopeFlow context to flow through the reactive pipeline:

```java
@RestController
public class ReactiveOrderController {

    @GetMapping("/orders/{id}")
    public Mono<Order> getOrder(@PathVariable String id) {
        // ScopeFlow context established by WebFilter or similar
        return orderRepository.findById(id)
            .doOnNext(order -> {
                // Context propagated here via Micrometer accessor
                log.info("Found order: {}", order.getId());
            });
    }
}
```
