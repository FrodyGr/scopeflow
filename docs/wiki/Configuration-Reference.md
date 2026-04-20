# Configuration Reference

## Spring Boot Properties

All properties are prefixed with `scopeflow.`:

### Master switch

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `scopeflow.enabled` | `boolean` | `true` | Enable/disable all ScopeFlow auto-configuration |

### MDC Configuration

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `scopeflow.mdc.enabled` | `boolean` | `true` | Enable/disable MDC propagation |
| `scopeflow.mdc.keys` | `Set<String>` | `(empty)` | Keys to propagate to MDC. Empty = all keys |
| `scopeflow.mdc.prefix` | `String` | `""` | Prefix for MDC keys (e.g., `"sf."`) |

### HTTP Interceptor

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `scopeflow.http.server.generate-request-scope` | `boolean` | `true` | Auto-create scope per HTTP request |
| `scopeflow.http.server.generate-request-id` | `boolean` | `true` | Generate UUID request ID if not in header |
| `scopeflow.http.server.request-id-header` | `String` | `X-Request-ID` | HTTP header name for request ID |

### Example configurations

**Development** (everything on, allow all):
```properties
scopeflow.enabled=true
scopeflow.mdc.enabled=true
scopeflow.mdc.keys=
spring.threads.virtual.enabled=true
```

**Production** (restricted keys, custom header):
```properties
scopeflow.enabled=true
scopeflow.mdc.enabled=true
scopeflow.mdc.keys=request.id,tenant.id,user.id,trace.id
scopeflow.mdc.prefix=sf.
scopeflow.http.server.request-id-header=X-Correlation-ID
```

**Minimal** (disabled MDC, only request scoping):
```properties
scopeflow.enabled=true
scopeflow.mdc.enabled=false
scopeflow.http.server.generate-request-scope=true
```

---

## ScopeFlowBuilder API

For non-Spring or custom configurations:

```java
ScopeFlow scopeFlow = ScopeFlowBuilder.create()

    // Add propagators (order matters!)
    .propagator(new MdcPropagator(MdcKeyPolicy.allowAll()))
    .propagator(OtelBaggagePropagator.create())
    .propagator(myCustomPropagator)

    // Set key policy (controls which keys can be put into context)
    .keyPolicy(ContextKeyPolicy.allowAll())          // default
    .keyPolicy(ContextKeyPolicy.allowList(Set.of(    // restrictive
        "request.id", "tenant.id", "user.id")))
    .keyPolicy(ContextKeyPolicy.denyList(Set.of(     // block sensitive
        "password", "token", "secret")))

    .build();
```

### Builder methods

| Method | Description |
|--------|-------------|
| `propagator(Propagator)` | Add a propagator (multiple allowed) |
| `keyPolicy(ContextKeyPolicy)` | Set the key filtering policy |
| `build()` | Create the `ScopeFlow` instance |

---

## MdcKeyPolicy API

| Factory Method | Description |
|---------------|-------------|
| `MdcKeyPolicy.allowAll()` | Propagate all keys to MDC |
| `MdcKeyPolicy.allowAll(prefix)` | Propagate all keys with prefix |
| `MdcKeyPolicy.of(Set<String> keys)` | Only propagate listed keys |
| `MdcKeyPolicy.of(Set<String> keys, String prefix)` | Listed keys with prefix |

### Instance methods

| Method | Description |
|--------|-------------|
| `shouldPropagate(String key)` | Whether this key should go to MDC |
| `mdcKey(String contextKey)` | Transform context key to MDC key (prefix) |
| `isAllowAll()` | Whether this is an allow-all policy |

---

## OtelBaggagePropagator API

| Factory Method | Description |
|---------------|-------------|
| `OtelBaggagePropagator.create()` | Propagate all keys to Baggage |
| `OtelBaggagePropagator.create(Set<String> keys)` | Only propagate listed keys |

---

## Propagator ordering

Propagators run in `order()` sequence. Lower numbers run first:

| Propagator | order() | Rationale |
|------------|---------|-----------|
| `MdcPropagator` | -100 | MDC should be set before other propagators log |
| Custom propagators | 0 | Default |
| `OtelBaggagePropagator` | 100 | OTel runs after MDC is set |

To customize ordering in your propagator:
```java
@Override
public int order() {
    return -50; // runs after MDC but before default
}
```
